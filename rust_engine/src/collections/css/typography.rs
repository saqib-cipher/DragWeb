use crate::models::{make_bean, BlockPalette};

pub struct Typography;

impl Typography {
    pub fn load() -> BlockPalette {
        let mut palette = BlockPalette::new("Typography", -13070788); // 0xFF388E3C

        palette.push(make_bean("", "setColor", "color: %s", " ", -13070788, "color: %1$s;", vec!["#000000"]));
        palette.push(make_bean("", "setFontSize", "font-size: %s", " ", -13070788, "font-size: %1$s;", vec!["16px"]));
        palette.push(make_bean("", "setFontWeight", "font-weight: %m.fontWeight", " ", -13070788, "font-weight: %1$s;", vec!["bold"]));
        palette.push(make_bean("", "setTextAlign", "text-align: %m.textAlign", " ", -13070788, "text-align: %1$s;", vec!["center"]));

        palette
    }
}

