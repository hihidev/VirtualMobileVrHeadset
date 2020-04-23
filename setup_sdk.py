import re
import urllib
import zipfile

f = urllib.urlopen("https://developer.oculus.com/downloads/package/oculus-mobile-sdk/1.32.0/")
s = f.read()
result = re.search("https:\/\/securecdn\.oculus\.com\/binaries\/download\/\?id=[0-9]+&amp;access_token=[0-9A-Za-z%]+", s)
urllib.urlretrieve(result.group(0), "ovr_sdk.zip")
with zipfile.ZipFile("ovr_sdk.zip", 'r') as zip_ref:
    zip_ref.extractall("ovr_sdk")

with open('ovr_sdk/VrSamples/SampleFramework/Projects/Android/jni/Android.mk', 'r') as file :
  filedata = file.read()

# Replace the target string
filedata = filedata.replace('include ../../../../cflags.mk', 'include $(LOCAL_PATH)/../../../../../cflags.mk')

# Write the file out again
with open('ovr_sdk/VrSamples/SampleFramework/Projects/Android/jni/Android.mk', 'w') as file:
  file.write(filedata)