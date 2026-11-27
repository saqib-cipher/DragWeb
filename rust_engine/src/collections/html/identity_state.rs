use crate::models::{make_bean, BlockPalette};

pub struct IdentityState;

impl IdentityState {
    pub fn load() -> BlockPalette {
        let mut palette = BlockPalette::new("Identity & State", -12345273); // 0xFF43A047

        palette.push(make_bean("Attributes", "htmlAttrId", "id=\" %s \"", " ", -12345273, "id=\"%1$s\"", vec!["main_box"]));
        palette.push(make_bean("", "htmlAttrClass", "class=\" %s \"", " ", -12345273, "class=\"%1$s\"", vec!["container"]));
        palette.push(make_bean("", "htmlAttrName", "name=\" %s \"", " ", -12345273, "name=\"%1$s\"", vec![""]));
        palette.push(make_bean("", "htmlAttrValue", "value=\" %s \"", " ", -12345273, "value=\"%1$s\"", vec![""]));
        palette.push(make_bean("", "htmlAttrType", "type=\" %m.htmlInputType \"", " ", -12345273, "type=\"%1$s\"", vec!["text"]));
        palette.push(make_bean("", "htmlAttrTitle", "title=\" %s \"", " ", -12345273, "title=\"%1$s\"", vec![""]));
        palette.push(make_bean("", "htmlAttrRole", "role=\" %m.htmlRole \"", " ", -12345273, "role=\"%1$s\"", vec!["region"]));
        palette.push(make_bean("", "htmlAttrAriaLabel", "aria-label=\" %s \"", " ", -12345273, "aria-label=\"%1$s\"", vec![""]));
        palette.push(make_bean("", "htmlAttrData", "data- %s =\" %s \"", " ", -12345273, "data-%1$s=\"%2$s\"", vec!["key", "value"]));
        palette.push(make_bean("", "htmlAttrGeneric", "%s =\" %s \"", " ", -12345273, "%1$s=\"%2$s\"", vec!["attr", "value"]));
        palette.push(make_bean("Media & Links", "htmlAttrSrc", "src=\" %s \"", " ", -12345273, "src=\"%1$s\"", vec![""]));
        palette.push(make_bean("", "htmlAttrAlt", "alt=\" %s \"", " ", -12345273, "alt=\"%1$s\"", vec![""]));
        palette.push(make_bean("", "htmlAttrHref", "href=\" %s \"", " ", -12345273, "href=\"%1$s\"", vec!["#"]));
        palette.push(make_bean("", "htmlAttrTarget", "target=\" %m.htmlTarget \"", " ", -12345273, "target=\"%1$s\"", vec!["_blank"]));
        palette.push(make_bean("", "htmlAttrControls", "controls", " ", -12345273, "controls", vec![]));
        palette.push(make_bean("Form Fields", "htmlAttrPlaceholder", "placeholder=\" %s \"", " ", -12345273, "placeholder=\"%1$s\"", vec![""]));
        palette.push(make_bean("", "htmlAttrFor", "for=\" %s \"", " ", -12345273, "for=\"%1$s\"", vec![""]));
        palette.push(make_bean("", "htmlAttrAction", "action=\" %s \"", " ", -12345273, "action=\"%1$s\"", vec![""]));
        palette.push(make_bean("", "htmlAttrMethod", "method=\" %m.htmlMethod \"", " ", -12345273, "method=\"%1$s\"", vec!["GET"]));
        palette.push(make_bean("", "htmlAttrDisabled", "disabled", " ", -12345273, "disabled", vec![]));
        palette.push(make_bean("", "htmlAttrChecked", "checked", " ", -12345273, "checked", vec![]));
        palette.push(make_bean("", "htmlAttrReadonly", "readonly", " ", -12345273, "readonly", vec![]));
        palette.push(make_bean("", "htmlAttrRequired", "required", " ", -12345273, "required", vec![]));

        palette
    }
}

