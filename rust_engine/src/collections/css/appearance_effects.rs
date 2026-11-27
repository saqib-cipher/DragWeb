use crate::models::{make_bean, BlockPalette};

pub struct AppearanceEffects;

impl AppearanceEffects {
    pub fn load() -> BlockPalette {
        let mut palette = BlockPalette::new("Appearance & Effects", -9920712); // #689F38

        palette.push(make_bean("", "setBackgroundColor", "background-color %m.color", " ", -9920712, "background-color: %1$s;", vec!["#ffffff"]));
        palette.push(make_bean("", "setBorder", "border %d %m.unit %m.borderStyle %m.color", " ", -9920712, "border: %1$s%2$s %3$s %4$s;", vec!["1", "px", "solid", "#cccccc"]));
        palette.push(make_bean("", "setRadius", "border-radius %d %m.unit", " ", -9920712, "border-radius: %1$s%2$s;", vec!["8", "px"]));
        palette.push(make_bean("", "setBoxShadow", "box-shadow x %d px y %d px blur %d px color %m.color", " ", -6447836, "box-shadow: %1$spx %2$spx %3$spx %4$s;", vec!["0", "2", "8", "rgba(0,0,0,0.1)"]));

        palette
    }
}

