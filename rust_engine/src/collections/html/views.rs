use crate::models::{make_bean, make_bean_spec2, BlockPalette};

pub struct Views;

impl Views {
    pub fn load() -> BlockPalette {
        let mut palette = BlockPalette::new("Views", -14776091); // 0xFF1E88E5

        // 1. Container Layout (e-shaped, with slot for child views)
        palette.push(make_bean_spec2(
            "Container Layout",
            "view_convert",
            "Convert : %m.Convert",
            "children",
            "e",
            -14776091,
            "<%s\n%s>\n%s\n</%s>",
            vec!["div"],
        ));

        // 2. View / Widget (c-shaped, leaf / self-closing / inline content)
        palette.push(make_bean(
            "View / Widget",
            "view_convert_leaf",
            "Convert : %m.Convert",
            "c",
            -14776091,
            "<%s %s />",
            vec!["p"],
        ));

        palette
    }
}

