How To Build
============

Put the [prebuild openvpn binary](https://github.com/CyanogenMod/android_external_openvpn/tree/ics) to "assets/openvpn" then:

Method 1. Build with android soure tree
-----------------------------------------------------

    # under root of android source tree
    source build/envsetup.sh
    breakfast `your device model`
    cd `OpenVpn folder`
    mm

Method 2. Using maven and ndk (recommanded)
-----------------------------

Put your signkey at `OpenVpn folder`/signkey.keystore (optional)

    export ANDROID_NDK_HOME=`your ndk path`
    export ANDROID_HOME=`your sdk path`
    mvn install # or with -Djarsigner.storepass=KEYPASS

