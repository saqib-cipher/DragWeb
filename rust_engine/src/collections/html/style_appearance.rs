use crate::models::{make_bean, BlockPalette};

pub struct StyleAppearance;

impl StyleAppearance {
    pub fn load() -> BlockPalette {
        let mut palette = BlockPalette::new("Style & Appearance", -9920712); // #689F38

        palette.push(make_bean("Colors & Borders", "setBackgroundColor", "background-color %m.color", " ", -9920712, "background-color: %1$s;", vec!["#ffffff"]));
        palette.push(make_bean("", "setBackground", "background %m.color", " ", -9920712, "background: %1$s;", vec!["#ffffff"]));
        palette.push(make_bean("", "setBorder", "border %d %m.unit %m.borderStyle %m.color", " ", -9920712, "border: %1$s%2$s %3$s %4$s;", vec!["1", "px", "solid", "#e0e0e0"]));
        palette.push(make_bean("", "setRadius", "border-radius %d %m.unit", " ", -9920712, "border-radius: %1$s%2$s;", vec!["8", "px"]));
        palette.push(make_bean("", "setRadiusMulti", "border-radius TL %d %m.unit TR %d %m.unit BR %d %m.unit BL %d %m.unit", " ", -9920712, "border-radius: %1$s%2$s %3$s%4$s %5$s%6$s %7$s%8$s;", vec!["8", "px", "8", "px", "8", "px", "8", "px"]));
        palette.push(make_bean("Effects", "setBoxShadow", "box-shadow x %d px y %d px blur %d px color %m.color", " ", -6447836, "box-shadow: %1$spx %2$spx %3$spx %4$s;", vec!["0", "2", "4", "rgba(0,0,0,0.1)"]));
        palette.push(make_bean("", "setOpacity", "opacity %d", " ", -16611119, "opacity: %1$s;", vec!["1.0"]));
        palette.push(make_bean("", "setCursor", "cursor %m.cursor", " ", -15108462, "cursor: %1$s;", vec!["pointer"]));
        palette.push(make_bean("", "cssPropertyGeneric", "%s: %s", " ", -12232092, "%1$s: %2$s;", vec!["property", "value"]));

        palette
    }
}

