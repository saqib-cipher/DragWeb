use crate::models::{make_bean, BlockPalette};

pub struct AppearanceEffects;

impl AppearanceEffects {
    pub fn load() -> BlockPalette {
        let mut palette = BlockPalette::new("Appearance & Effects", -7461718); // 0xFF8E24AA

        palette.push(make_bean("", "setBackgroundColor", "background-color: %s", " ", -7461718, "background-color: %1$s;", vec!["#ffffff"]));
        palette.push(make_bean("", "setBorder", "border: %s", " ", -7461718, "border: %1$s;", vec!["1px solid #ccc"]));
        palette.push(make_bean("", "setRadius", "border-radius: %s", " ", -7461718, "border-radius: %1$s;", vec!["8px"]));
        palette.push(make_bean("", "setBoxShadow", "box-shadow: %s", " ", -7461718, "box-shadow: %1$s;", vec!["0 2px 8px rgba(0,0,0,0.1)"]));

        palette
    }
}

