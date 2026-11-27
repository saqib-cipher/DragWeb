use crate::models::{make_bean, BlockPalette};

pub struct TextTypography;

impl TextTypography {
    pub fn load() -> BlockPalette {
        let mut palette = BlockPalette::new("Text & Typography", -13022805); // 0xFF3949AB

        palette.push(make_bean("Content", "htmlText", "text: %s", " ", -13022805, "%1$s", vec!["Hello, world!"]));
        palette.push(make_bean("Styling", "setColor", "color: %s", " ", -13022805, "color: %1$s;", vec!["#000000"]));
        palette.push(make_bean("", "setFontSize", "font-size: %s", " ", -13022805, "font-size: %1$s;", vec!["14px"]));
        palette.push(make_bean("", "setFontWeight", "font-weight: %m.fontWeight", " ", -13022805, "font-weight: %1$s;", vec!["normal"]));
        palette.push(make_bean("", "setTextAlign", "text-align: %m.textAlign", " ", -13022805, "text-align: %1$s;", vec!["left"]));
        palette.push(make_bean("", "setTextDecoration", "text-decoration: %m.textDecoration", " ", -13022805, "text-decoration: %1$s;", vec!["none"]));
        palette.push(make_bean("", "setTextTransform", "text-transform: %m.textTransform", " ", -13022805, "text-transform: %1$s;", vec!["none"]));
        palette.push(make_bean("", "setLineHeight", "line-height: %s", " ", -13022805, "line-height: %1$s;", vec!["1.5"]));
        palette.push(make_bean("", "setFontFamily", "font-family: %s", " ", -13022805, "font-family: %1$s;", vec!["sans-serif"]));

        palette
    }
}

