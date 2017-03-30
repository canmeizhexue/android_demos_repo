对Android Library的使用说明

myapplicationsecondlibrary-debug是通过导入已经存在的AAR来创建的
创建方式  File--->New--->New Module--->Import JAR/AAR Package


myapplicationlibrary是通过导入第三方Android library的源代码来创建的，，这里是复制了一份，而不是建立和第三方代码的映射
创建方式 File--->New--->Import Module

firstlibrary和secondlibrary分别是为本工程新建的library
创建方式  File--->New--->New Module--->Android Library

<!--默认情况下library里面的所有资源都会直接暴露给library的使用者，通过在library的res/values/下面配置public.xml文件可以只暴露指定的资源给使用者，，，，但是实际效果并不是-->