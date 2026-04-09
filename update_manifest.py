import xml.etree.ElementTree as ET

tree = ET.parse('source/app/src/main/AndroidManifest.xml')
root = tree.getroot()
app = root.find('application')
android_namespace = 'http://schemas.android.com/apk/res/android'

# Add PreviewFullscreenActivity
new_activity = ET.Element('activity')
new_activity.set(f'{{{android_namespace}}}name', '.PreviewFullscreenActivity')
app.append(new_activity)

tree.write('source/app/src/main/AndroidManifest.xml')
