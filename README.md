How To Build
============

Put the [prebuild openvpn binary](https://github.com/kghost/android_external_openvpn/tree/ics) to "assets/openvpn" then:

Method 1. Build with android soure tree (recommanded)
-----------------------------------------------------

    # under root of android source tree
    source build/envsetup.sh
    breakfast `your device model`
    cd `OpenVpn folder`
    mm

Method 2. Using maven and ndk
-----------------------------

Put your signkey at `OpenVpn folder`/signkey.keystore (optional)

You'd better apply [this patch](https://github.com/kghost/maven-android-plugin/commit/38599d329cd9bdb87e2e906133e0110890199d0e "Use origin native library filename as dest name") to android-maven-plugin first

    export ANDROID_NDK_HOME=`your ndk path`
    export ANDROID_HOME=`your sdk path`
    mvn install # or with -Djarsigner.storepass=KEYPASS

