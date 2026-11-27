use crate::models::{make_bean, BlockPalette};

pub struct LayoutBox;

impl LayoutBox {
    pub fn load() -> BlockPalette {
        let mut palette = BlockPalette::new("Layout & Box", -16611119); // 0xFF0288D1

        palette.push(make_bean("", "setWidth", "width: %s", " ", -16611119, "width: %1$s;", vec!["100%"]));
        palette.push(make_bean("", "setHeight", "height: %s", " ", -16611119, "height: %1$s;", vec!["auto"]));
        palette.push(make_bean("", "setMargin", "margin: %s", " ", -16611119, "margin: %1$s;", vec!["0px"]));
        palette.push(make_bean("", "setPadding", "padding: %s", " ", -16611119, "padding: %1$s;", vec!["16px"]));
        palette.push(make_bean("", "setDisplay", "display: %m.display", " ", -16611119, "display: %1$s;", vec!["flex"]));
        palette.push(make_bean("", "setPosition", "position: %m.position", " ", -16611119, "position: %1$s;", vec!["relative"]));

        palette
    }
}

