use crate::models::{make_bean, BlockPalette};

pub struct StyleAppearance;

impl StyleAppearance {
    pub fn load() -> BlockPalette {
        let mut palette = BlockPalette::new("Style & Appearance", -7461718); // 0xFF8E24AA

        palette.push(make_bean("Colors & Borders", "setBackgroundColor", "background-color: %s", " ", -7461718, "background-color: %1$s;", vec!["#ffffff"]));
        palette.push(make_bean("", "setBackground", "background: %s", " ", -7461718, "background: %1$s;", vec!["#ffffff"]));
        palette.push(make_bean("", "setBorder", "border: %s", " ", -7461718, "border: %1$s;", vec!["1px solid #e0e0e0"]));
        palette.push(make_bean("", "setRadius", "border-radius: %s", " ", -7461718, "border-radius: %1$s;", vec!["8px"]));
        palette.push(make_bean("Effects", "setBoxShadow", "box-shadow: %s", " ", -7461718, "box-shadow: %1$s;", vec!["0 2px 4px rgba(0,0,0,0.1)"]));
        palette.push(make_bean("", "setOpacity", "opacity: %s", " ", -7461718, "opacity: %1$s;", vec!["1.0"]));
        palette.push(make_bean("", "setCursor", "cursor: %m.cursor", " ", -7461718, "cursor: %1$s;", vec!["pointer"]));
        palette.push(make_bean("", "cssPropertyGeneric", "%s: %s", " ", -7461718, "%1$s: %2$s;", vec!["property", "value"]));

        palette
    }
}

