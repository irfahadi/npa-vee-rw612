rootProject.name = "npa-rw612-frdm"
include("vee-port", "vee-port:front-panel", "vee-port:mock", "vee-port:image-generator")
include("apps:simpleGFX", "apps:HelloWorld", "apps:SmartGreenhouse")

project(":vee-port:front-panel").projectDir = file("vee-port/extensions/front-panel")
project(":vee-port:mock").projectDir = file("vee-port/mock")
project(":vee-port:image-generator").projectDir = file("vee-port/extensions/image-generator")

include("vee-port:validation:core")
include("vee-port:validation:ecom-wifi")    
include("vee-port:validation:net")
include("vee-port:validation:security")
include("vee-port:validation:ssl")