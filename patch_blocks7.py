import re

with open('source/app/src/main/java/sketchweb/gl/LogicBlockActivity.java', 'r') as f:
    content = f.read()

# Enhance Variables & Logic Actions, implement Color Picker inside the block logic for setColor / setBackground
old_show_value_input = """    private void showValueInputForBlock(BlockDef eventDef, BlockDef actionDef) {
        String hint = getValueHint(actionDef);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 16, 48, 0);

        TextInputLayout til = new TextInputLayout(this);
        til.setHint(hint);
        TextInputEditText input = new TextInputEditText(this);
        til.addView(input);
        layout.addView(til);

        new MaterialAlertDialogBuilder(this)
            .setTitle(actionDef.label)
            .setView(layout)
            .setPositiveButton("Add Block", (d, w) -> {
                String value = input.getText() != null ? input.getText().toString().trim() : "";
                createBlock(eventDef, actionDef, value);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }"""

new_show_value_input = """    private void showValueInputForBlock(BlockDef eventDef, BlockDef actionDef) {
        String hint = getValueHint(actionDef);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 16, 48, 0);

        TextInputLayout til = new TextInputLayout(this);
        til.setHint(hint);
        TextInputEditText input = new TextInputEditText(this);
        til.addView(input);

        // Add color picker preview feature if it's a color attribute
        boolean isColor = actionDef.id.equals("setColor") || actionDef.id.equals("setBackground");
        if (isColor) {
            input.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    try {
                        til.setBoxBackgroundColor(Color.parseColor(s.toString()));
                    } catch (Exception e) {
                        til.setBoxBackgroundColor(Color.TRANSPARENT);
                    }
                }
                @Override public void afterTextChanged(android.text.Editable s) {}
            });
        }

        layout.addView(til);

        new MaterialAlertDialogBuilder(this)
            .setTitle(actionDef.label)
            .setView(layout)
            .setPositiveButton("Add Block", (d, w) -> {
                String value = input.getText() != null ? input.getText().toString().trim() : "";
                createBlock(eventDef, actionDef, value);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }"""

content = content.replace(old_show_value_input, new_show_value_input)

with open('source/app/src/main/java/sketchweb/gl/LogicBlockActivity.java', 'w') as f:
    f.write(content)
