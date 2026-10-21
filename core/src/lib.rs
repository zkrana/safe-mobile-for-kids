//! Parental-control engine: policy, PIN handling and the allow/block decision.
//! The Android shell reports what happened and renders the UI; every rule lives here.

use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jint, jstring};
use jni::JNIEnv;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};

const YOUTUBE: &[&str] = &["com.google.android.youtube", "app.revanced.android.youtube"];
const OWN_APP: &str = "com.gamecontrol.guard";

/// Stores, game hubs and installers on Redmi/MIUI/HyperOS and stock Android.
const DEFAULT_BLOCKED: &[&str] = &[
    "com.xiaomi.mipicks",
    "com.xiaomi.market",
    "com.xiaomi.glgm",
    "com.xiaomi.gamecenter",
    "com.xiaomi.gamecenter.sdk.service",
    "com.android.vending",
    "com.miui.packageinstaller",
    "com.google.android.packageinstaller",
    "com.android.packageinstaller",
];

/// Still usable at bedtime (calls, alarms, notification shade).
const BEDTIME_SYSTEM: &[&str] = &[
    "com.android.systemui",
    "com.android.dialer",
    "com.google.android.dialer",
    "com.android.incallui",
    "com.android.contacts",
    "com.android.deskclock",
    "com.google.android.deskclock",
];

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(i32)]
pub enum Action {
    Allow = 0,
    /// Send the child to the home screen.
    Block = 1,
    /// Open YouTube Kids instead of YouTube.
    RedirectYoutubeKids = 2,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(default)]
pub struct Bedtime {
    pub enabled: bool,
    /// Minutes after midnight.
    pub start: u32,
    pub end: u32,
}

impl Default for Bedtime {
    fn default() -> Self {
        Bedtime { enabled: false, start: 21 * 60, end: 7 * 60 }
    }
}

impl Bedtime {
    pub fn active_at(&self, minute: u32) -> bool {
        if !self.enabled || self.start == self.end {
            false
        } else if self.start < self.end {
            minute >= self.start && minute < self.end
        } else {
            minute >= self.start || minute < self.end
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(default)]
pub struct Policy {
    pub blocked_packages: Vec<String>,
    /// Always usable, even at bedtime or if flagged as a game.
    pub allowed_packages: Vec<String>,
    pub block_games: bool,
    pub youtube_redirect: bool,
    /// Stop the child from reaching this app's accessibility / admin / app-info screens.
    pub protect_settings: bool,
    pub bedtime: Bedtime,
    pub pin_salt: String,
    pub pin_hash: String,
}

impl Default for Policy {
    fn default() -> Self {
        Policy {
            blocked_packages: DEFAULT_BLOCKED.iter().map(|s| s.to_string()).collect(),
            allowed_packages: vec![],
            block_games: true,
            youtube_redirect: true,
            protect_settings: true,
            bedtime: Bedtime::default(),
            pin_salt: String::new(),
            pin_hash: String::new(),
        }
    }
}

fn hash_pin(salt: &str, pin: &str) -> String {
    let digest = Sha256::digest(format!("{salt}:{pin}").as_bytes());
    digest.iter().map(|b| format!("{b:02x}")).collect()
}

fn valid_pin(pin: &str) -> bool {
    (4..=12).contains(&pin.len()) && pin.bytes().all(|b| b.is_ascii_digit())
}

fn clean_list(list: &[String]) -> Vec<String> {
    let mut out: Vec<String> = Vec::new();
    for item in list {
        let item = item.trim().to_string();
        if !item.is_empty() && !out.contains(&item) {
            out.push(item);
        }
    }
    out
}

impl Policy {
    pub fn set_pin(&mut self, pin: &str, salt: &str) -> bool {
        if !valid_pin(pin) || salt.is_empty() {
            return false;
        }
        self.pin_salt = salt.to_string();
        self.pin_hash = hash_pin(salt, pin);
        true
    }

    pub fn check_pin(&self, pin: &str) -> bool {
        if self.pin_hash.is_empty() {
            return false;
        }
        let got = hash_pin(&self.pin_salt, pin);
        // Compare without early exit.
        got.len() == self.pin_hash.len()
            && got.bytes().zip(self.pin_hash.bytes()).fold(0u8, |acc, (a, b)| acc | (a ^ b)) == 0
    }

    pub fn normalize(&mut self) {
        self.blocked_packages = clean_list(&self.blocked_packages);
        self.allowed_packages = clean_list(&self.allowed_packages);
        self.bedtime.start %= 24 * 60;
        self.bedtime.end %= 24 * 60;
    }
}

/// What the Android shell knows about the window that just came to the front.
#[derive(Debug, Default, Deserialize)]
#[serde(default)]
pub struct Ctx {
    pub package: String,
    pub is_game: bool,
    pub is_launcher: bool,
    pub minute_of_day: u32,
    /// Parent has entered the PIN recently.
    pub unlocked: bool,
    /// A system settings screen is showing this app's name.
    pub mentions_us: bool,
}

pub fn decide(p: &Policy, c: &Ctx) -> Action {
    let pkg = c.package.as_str();
    if c.unlocked || pkg.is_empty() || pkg == OWN_APP {
        return Action::Allow;
    }
    if p.protect_settings && c.mentions_us {
        return Action::Block;
    }
    if p.allowed_packages.iter().any(|a| a == pkg) {
        return Action::Allow;
    }
    if p.bedtime.active_at(c.minute_of_day) {
        return if c.is_launcher || BEDTIME_SYSTEM.contains(&pkg) {
            Action::Allow
        } else {
            Action::Block
        };
    }
    if p.youtube_redirect && YOUTUBE.contains(&pkg) {
        return Action::RedirectYoutubeKids;
    }
    if (p.block_games && c.is_game) || p.blocked_packages.iter().any(|b| b == pkg) {
        return Action::Block;
    }
    Action::Allow
}

// ---------------------------------------------------------------- JNI layer

fn read(env: &mut JNIEnv, s: &JString) -> Option<String> {
    env.get_string(s).ok().map(String::from)
}

fn ret(env: &mut JNIEnv, s: &str) -> jstring {
    env.new_string(s).map(|j| j.into_raw()).unwrap_or(std::ptr::null_mut())
}

fn parse_policy(json: &str) -> Option<Policy> {
    serde_json::from_str(json).ok()
}

#[no_mangle]
pub extern "system" fn Java_com_gamecontrol_guard_Native_defaultPolicy(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let json = serde_json::to_string(&Policy::default()).unwrap_or_default();
    ret(&mut env, &json)
}

/// Validate + clean a policy JSON. Returns "" if it cannot be parsed.
#[no_mangle]
pub extern "system" fn Java_com_gamecontrol_guard_Native_normalize(
    mut env: JNIEnv,
    _class: JClass,
    policy: JString,
) -> jstring {
    let out = read(&mut env, &policy)
        .and_then(|j| parse_policy(&j))
        .and_then(|mut p| {
            p.normalize();
            serde_json::to_string(&p).ok()
        })
        .unwrap_or_default();
    ret(&mut env, &out)
}

/// Returns the updated policy JSON, or "" if the PIN is not 4-12 digits.
#[no_mangle]
pub extern "system" fn Java_com_gamecontrol_guard_Native_setPin(
    mut env: JNIEnv,
    _class: JClass,
    policy: JString,
    pin: JString,
    salt: JString,
) -> jstring {
    let out = (|| {
        let mut p = parse_policy(&read(&mut env, &policy)?)?;
        let pin = read(&mut env, &pin)?;
        let salt = read(&mut env, &salt)?;
        if !p.set_pin(&pin, &salt) {
            return None;
        }
        serde_json::to_string(&p).ok()
    })()
    .unwrap_or_default();
    ret(&mut env, &out)
}

#[no_mangle]
pub extern "system" fn Java_com_gamecontrol_guard_Native_checkPin(
    mut env: JNIEnv,
    _class: JClass,
    policy: JString,
    pin: JString,
) -> jboolean {
    let ok = (|| {
        let p = parse_policy(&read(&mut env, &policy)?)?;
        Some(p.check_pin(&read(&mut env, &pin)?))
    })()
    .unwrap_or(false);
    ok as jboolean
}

#[no_mangle]
pub extern "system" fn Java_com_gamecontrol_guard_Native_decide(
    mut env: JNIEnv,
    _class: JClass,
    policy: JString,
    ctx: JString,
) -> jint {
    (|| {
        let p = parse_policy(&read(&mut env, &policy)?)?;
        let c: Ctx = serde_json::from_str(&read(&mut env, &ctx)?).ok()?;
        Some(decide(&p, &c) as jint)
    })()
    .unwrap_or(Action::Allow as jint)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn ctx(pkg: &str) -> Ctx {
        Ctx { package: pkg.into(), ..Ctx::default() }
    }

    #[test]
    fn blocks_stores_and_installers() {
        let p = Policy::default();
        for pkg in ["com.xiaomi.mipicks", "com.android.vending", "com.miui.packageinstaller"] {
            assert_eq!(decide(&p, &ctx(pkg)), Action::Block, "{pkg}");
        }
    }

    #[test]
    fn games_blocked_unless_allowed() {
        let mut p = Policy::default();
        let game = Ctx { is_game: true, ..ctx("com.some.game") };
        assert_eq!(decide(&p, &game), Action::Block);
        p.allowed_packages.push("com.some.game".into());
        assert_eq!(decide(&p, &game), Action::Allow);
        p.allowed_packages.clear();
        p.block_games = false;
        assert_eq!(decide(&p, &game), Action::Allow);
    }

    #[test]
    fn youtube_goes_to_kids_and_kids_is_fine() {
        let p = Policy::default();
        assert_eq!(decide(&p, &ctx("com.google.android.youtube")), Action::RedirectYoutubeKids);
        assert_eq!(decide(&p, &ctx("com.google.android.apps.youtube.kids")), Action::Allow);
    }

    #[test]
    fn settings_screens_about_us_are_protected() {
        let p = Policy::default();
        let c = Ctx { mentions_us: true, ..ctx("com.android.settings") };
        assert_eq!(decide(&p, &c), Action::Block);
        assert_eq!(decide(&p, &ctx("com.android.settings")), Action::Allow);
    }

    #[test]
    fn parent_unlock_allows_everything() {
        let p = Policy::default();
        let c = Ctx { unlocked: true, mentions_us: true, ..ctx("com.android.vending") };
        assert_eq!(decide(&p, &c), Action::Allow);
    }

    #[test]
    fn bedtime_wraps_midnight_and_keeps_basics() {
        let mut p = Policy::default();
        p.bedtime = Bedtime { enabled: true, start: 21 * 60, end: 7 * 60 };
        assert!(p.bedtime.active_at(23 * 60));
        assert!(p.bedtime.active_at(60));
        assert!(!p.bedtime.active_at(12 * 60));
        let night = |pkg: &str| Ctx { minute_of_day: 23 * 60, ..ctx(pkg) };
        assert_eq!(decide(&p, &night("com.android.chrome")), Action::Block);
        assert_eq!(decide(&p, &night("com.android.dialer")), Action::Allow);
        assert_eq!(decide(&p, &night("com.google.android.youtube")), Action::Block);
        let launcher = Ctx { is_launcher: true, ..night("com.miui.home") };
        assert_eq!(decide(&p, &launcher), Action::Allow);
    }

    #[test]
    fn pin_roundtrip() {
        let mut p = Policy::default();
        assert!(!p.check_pin("1234"));
        assert!(!p.set_pin("12", "salt"));
        assert!(!p.set_pin("12ab", "salt"));
        assert!(p.set_pin("4821", "salt"));
        assert!(p.check_pin("4821"));
        assert!(!p.check_pin("4822"));
        assert!(!p.pin_hash.contains("4821"));
    }

    #[test]
    fn policy_json_survives_missing_fields_and_normalizes() {
        let mut p: Policy = serde_json::from_str(r#"{"blocked_packages":[" a ","a",""]}"#).unwrap();
        assert!(p.block_games);
        p.normalize();
        assert_eq!(p.blocked_packages, vec!["a".to_string()]);
    }
}
