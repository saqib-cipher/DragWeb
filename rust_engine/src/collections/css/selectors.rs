use crate::models::{make_bean, BlockPalette};

pub struct Selectors;

impl Selectors {
    pub fn load() -> BlockPalette {
        let mut palette = BlockPalette::new("Selectors", -15108462); // 0xFF1976D2

        palette.push(make_bean("Rules", "cssSelector", "Widget %selector", "c", -15108462, "%1$s {\n %2$s\n}\n", vec![".box"]));
        palette.push(make_bean("Pseudo-Classes", "cssHover", "%selector hover", "c", -15108462, "%1$s:hover {\n %2$s\n}\n", vec![".box"]));
        palette.push(make_bean("", "cssFocus", "%selector focus", "c", -15108462, "%1$s:focus {\n %2$s\n}\n", vec![".box"]));
        palette.push(make_bean("", "cssActive", "%selector active", "c", -15108462, "%1$s:active {\n %2$s\n}\n", vec![".box"]));
        palette.push(make_bean("Responsive", "cssMediaQuery", "@media (%s) {", "c", -15108462, "@media (%1$s) {\n %2$s\n}\n", vec!["max-width: 768px"]));

        palette
    }
}

