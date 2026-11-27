use crate::models::{make_bean, BlockPalette};

pub struct Events;

impl Events {
    pub fn load() -> BlockPalette {
        let mut palette = BlockPalette::new("JS Events", -4056997); // 0xFFC2185B

        palette.push(make_bean("Listeners", "jsAddEventOnVar", "%var addEventListener %m.event", "c", -4056997, "%1$s.addEventListener(\"%2$s\", (e) => {\n%3$s\n});\n", vec!["btn", "click"]));
        palette.push(make_bean("Lifecycle", "jsOnLoad", "on DOMContentLoaded", "c", -4056997, "document.addEventListener(\"DOMContentLoaded\", function() {\n%1$s\n});\n", vec![]));

        palette
    }
}

