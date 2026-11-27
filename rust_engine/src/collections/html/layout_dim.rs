use crate::models::{make_bean, BlockPalette};

pub struct LayoutDimensions;

impl LayoutDimensions {
    pub fn load() -> BlockPalette {
        let mut palette = BlockPalette::new("Layout & Dimensions", -1086464); // 0xFFEF6C00

        palette.push(make_bean("Dimensions", "setWidth", "width: %s", " ", -1086464, "width: %1$s;", vec!["100%"]));
        palette.push(make_bean("", "setHeight", "height: %s", " ", -1086464, "height: %1$s;", vec!["auto"]));
        palette.push(make_bean("", "setMinWidth", "min-width: %s", " ", -1086464, "min-width: %1$s;", vec!["0px"]));
        palette.push(make_bean("", "setMaxWidth", "max-width: %s", " ", -1086464, "max-width: %1$s;", vec!["100%"]));
        palette.push(make_bean("", "setMinHeight", "min-height: %s", " ", -1086464, "min-height: %1$s;", vec!["0px"]));
        palette.push(make_bean("", "setMaxHeight", "max-height: %s", " ", -1086464, "max-height: %1$s;", vec!["none"]));
        palette.push(make_bean("Spacing", "setMargin", "margin: %s", " ", -1086464, "margin: %1$s;", vec!["0px"]));
        palette.push(make_bean("", "setPadding", "padding: %s", " ", -1086464, "padding: %1$s;", vec!["16px"]));
        palette.push(make_bean("Flow & Layout", "setDisplay", "display: %m.display", " ", -1086464, "display: %1$s;", vec!["block"]));
        palette.push(make_bean("", "setPosition", "position: %m.position", " ", -1086464, "position: %1$s;", vec!["relative"]));
        palette.push(make_bean("", "setOverflow", "overflow: %m.overflow", " ", -1086464, "overflow: %1$s;", vec!["visible"]));
        palette.push(make_bean("Flex & Grid", "setFlexDirection", "flex-direction: %m.flexDirection", " ", -1086464, "flex-direction: %1$s;", vec!["row"]));
        palette.push(make_bean("", "setFlexWrap", "flex-wrap: %m.flexWrap", " ", -1086464, "flex-wrap: %1$s;", vec!["wrap"]));
        palette.push(make_bean("", "setJustifyContent", "justify-content: %m.justifyContent", " ", -1086464, "justify-content: %1$s;", vec!["center"]));
        palette.push(make_bean("", "setAlignItems", "align-items: %m.alignItems", " ", -1086464, "align-items: %1$s;", vec!["center"]));
        palette.push(make_bean("", "setGap", "gap: %s", " ", -1086464, "gap: %1$s;", vec!["8px"]));

        palette
    }
}

