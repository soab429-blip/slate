package yt.ren.slate.util.math;

class CrtAtan2 {
    private static final long SIGN = 0x8000000000000000L;
    private static final long ABS = 0x7fffffffffffffffL;
    private static final long INF = 0x7ff0000000000000L;
    private static final long QUIET = 0x8000000000000L;

    private static final double C_0_0625 = bits(0x3fb0000000000000L);
    private static final double C_256 = bits(0x4070000000000000L);
    private static final double C_HALF = bits(0x3fe0000000000000L);
    private static final double C_1_256 = bits(0x3f70000000000000L);
    private static final double C_1E8 = bits(0x3e45798ee2308c3aL);
    private static final double C_1E4 = bits(0x3f1a36e2eb1c432dL);
    private static final double C_2P100 = bits(0x4630000000000000L);
    private static final double R_A = bits(0x3fc99992b9802d7dL);
    private static final double R_B = bits(0x3fd5555555550877L);
    private static final double S_A = bits(0x3fb70c3192d751c2L);
    private static final double S_B = bits(0x3fbc718839b8c4ebL);
    private static final double S_C = bits(0x3fc2492482bd6be1L);
    private static final double S_D = bits(0x3fc99999999643a3L);
    private static final double S_E = bits(0x3fd5555555555538L);
    private static final double PIO2 = bits(0x3ff921fb54442d18L);
    private static final double PIO2_LO = bits(0x3c91a62633145c07L);
    private static final double PI_HI = bits(0x400921fb50000000L);
    private static final double PI_LO = bits(0x3e6110b4611a6263L);
    private static final double PI = bits(0x400921fb54442d18L);
    private static final double PIO4 = bits(0x3fe921fb54442d18L);
    private static final double PI3O4 = bits(0x4002d97c7f3321d2L);

    private static final long[] ATAN_HI = {
            0x3faff55b00000000L, 0x3fb0f99e00000000L, 0x3fb1f86d00000000L, 0x3fb2f71900000000L,
            0x3fb3f59f00000000L, 0x3fb4f3fd00000000L, 0x3fb5f23200000000L, 0x3fb6f03b00000000L,
            0x3fb7ee1800000000L, 0x3fb8ebc500000000L, 0x3fb9e94100000000L, 0x3fbae68a00000000L,
            0x3fbbe39e00000000L, 0x3fbce07c00000000L, 0x3fbddd2100000000L, 0x3fbed98c00000000L,
            0x3fbfd5ba00000000L, 0x3fc068d500000000L, 0x3fc0e6ad00000000L, 0x3fc1646500000000L,
            0x3fc1e1fa00000000L, 0x3fc25f6e00000000L, 0x3fc2dcbd00000000L, 0x3fc359e800000000L,
            0x3fc3d6ee00000000L, 0x3fc453ce00000000L, 0x3fc4d08700000000L, 0x3fc54d1800000000L,
            0x3fc5c98100000000L, 0x3fc645bf00000000L, 0x3fc6c1d400000000L, 0x3fc73dbd00000000L,
            0x3fc7b97b00000000L, 0x3fc8350b00000000L, 0x3fc8b06e00000000L, 0x3fc92ba300000000L,
            0x3fc9a6a800000000L, 0x3fca217e00000000L, 0x3fca9c2300000000L, 0x3fcb169600000000L,
            0x3fcb90d700000000L, 0x3fcc0ae500000000L, 0x3fcc84bf00000000L, 0x3fccfe6500000000L,
            0x3fcd77d500000000L, 0x3fcdf11000000000L, 0x3fce6a1400000000L, 0x3fcee2e100000000L,
            0x3fcf5b7500000000L, 0x3fcfd3d100000000L, 0x3fd025fa00000000L, 0x3fd061ee00000000L,
            0x3fd09dc500000000L, 0x3fd0d97e00000000L, 0x3fd1151a00000000L, 0x3fd1509700000000L,
            0x3fd18bf500000000L, 0x3fd1c73500000000L, 0x3fd2025500000000L, 0x3fd23d5600000000L,
            0x3fd2783700000000L, 0x3fd2b2f700000000L, 0x3fd2ed9800000000L, 0x3fd3281800000000L,
            0x3fd3627700000000L, 0x3fd39cb400000000L, 0x3fd3d6d100000000L, 0x3fd410cb00000000L,
            0x3fd44aa400000000L, 0x3fd4845a00000000L, 0x3fd4bdee00000000L, 0x3fd4f75f00000000L,
            0x3fd530ad00000000L, 0x3fd569d800000000L, 0x3fd5a2e000000000L, 0x3fd5dbc300000000L,
            0x3fd6148400000000L, 0x3fd64d1f00000000L, 0x3fd6859700000000L, 0x3fd6bdea00000000L,
            0x3fd6f61900000000L, 0x3fd72e2200000000L, 0x3fd7660700000000L, 0x3fd79dc600000000L,
            0x3fd7d56000000000L, 0x3fd80cd400000000L, 0x3fd8442200000000L, 0x3fd87b4b00000000L,
            0x3fd8b24d00000000L, 0x3fd8e92900000000L, 0x3fd91fde00000000L, 0x3fd9566d00000000L,
            0x3fd98cd500000000L, 0x3fd9c31600000000L, 0x3fd9f93000000000L, 0x3fda2f2300000000L,
            0x3fda64ee00000000L, 0x3fda9a9200000000L, 0x3fdad00f00000000L, 0x3fdb056400000000L,
            0x3fdb3a9100000000L, 0x3fdb6f9600000000L, 0x3fdba47300000000L, 0x3fdbd92800000000L,
            0x3fdc0db400000000L, 0x3fdc421900000000L, 0x3fdc765500000000L, 0x3fdcaa6800000000L,
            0x3fdcde5300000000L, 0x3fdd121500000000L, 0x3fdd45ae00000000L, 0x3fdd791f00000000L,
            0x3fddac6700000000L, 0x3fdddf8500000000L, 0x3fde127b00000000L, 0x3fde454800000000L,
            0x3fde77eb00000000L, 0x3fdeaa6500000000L, 0x3fdedcb600000000L, 0x3fdf0ede00000000L,
            0x3fdf40dd00000000L, 0x3fdf72b200000000L, 0x3fdfa45d00000000L, 0x3fdfd5e000000000L,
            0x3fe0039c00000000L, 0x3fe01c3400000000L, 0x3fe034b700000000L, 0x3fe04d2500000000L,
            0x3fe0657e00000000L, 0x3fe07dc300000000L, 0x3fe095f300000000L, 0x3fe0ae0e00000000L,
            0x3fe0c61400000000L, 0x3fe0de0500000000L, 0x3fe0f5e200000000L, 0x3fe10daa00000000L,
            0x3fe1255d00000000L, 0x3fe13cfb00000000L, 0x3fe1548500000000L, 0x3fe16bfa00000000L,
            0x3fe1835a00000000L, 0x3fe19aa500000000L, 0x3fe1b1dc00000000L, 0x3fe1c8fe00000000L,
            0x3fe1e00b00000000L, 0x3fe1f70400000000L, 0x3fe20de800000000L, 0x3fe224b700000000L,
            0x3fe23b7100000000L, 0x3fe2521700000000L, 0x3fe268a900000000L, 0x3fe27f2600000000L,
            0x3fe2958e00000000L, 0x3fe2abe200000000L, 0x3fe2c22100000000L, 0x3fe2d84c00000000L,
            0x3fe2ee6200000000L, 0x3fe3046400000000L, 0x3fe31a5200000000L, 0x3fe3302b00000000L,
            0x3fe345f000000000L, 0x3fe35ba000000000L, 0x3fe3713d00000000L, 0x3fe386c500000000L,
            0x3fe39c3900000000L, 0x3fe3b19800000000L, 0x3fe3c6e400000000L, 0x3fe3dc1c00000000L,
            0x3fe3f13f00000000L, 0x3fe4064f00000000L, 0x3fe41b4a00000000L, 0x3fe4303200000000L,
            0x3fe4450600000000L, 0x3fe459c600000000L, 0x3fe46e7200000000L, 0x3fe4830a00000000L,
            0x3fe4978f00000000L, 0x3fe4ac0000000000L, 0x3fe4c05e00000000L, 0x3fe4d4a800000000L,
            0x3fe4e8de00000000L, 0x3fe4fd0100000000L, 0x3fe5111000000000L, 0x3fe5250c00000000L,
            0x3fe538f500000000L, 0x3fe54cca00000000L, 0x3fe5608d00000000L, 0x3fe5743c00000000L,
            0x3fe587d800000000L, 0x3fe59b6000000000L, 0x3fe5aed600000000L, 0x3fe5c23900000000L,
            0x3fe5d58900000000L, 0x3fe5e8c600000000L, 0x3fe5fbf000000000L, 0x3fe60f0800000000L,
            0x3fe6220d00000000L, 0x3fe634ff00000000L, 0x3fe647de00000000L, 0x3fe65aab00000000L,
            0x3fe66d6600000000L, 0x3fe6800e00000000L, 0x3fe692a400000000L, 0x3fe6a52700000000L,
            0x3fe6b79800000000L, 0x3fe6c9f700000000L, 0x3fe6dc4400000000L, 0x3fe6ee7f00000000L,
            0x3fe700a700000000L, 0x3fe712be00000000L, 0x3fe724c300000000L, 0x3fe736b600000000L,
            0x3fe7489700000000L, 0x3fe75a6700000000L, 0x3fe76c2400000000L, 0x3fe77dd100000000L,
            0x3fe78f6b00000000L, 0x3fe7a0f400000000L, 0x3fe7b26c00000000L, 0x3fe7c3d300000000L,
            0x3fe7d52800000000L, 0x3fe7e66c00000000L, 0x3fe7f79e00000000L, 0x3fe808c000000000L,
            0x3fe819d000000000L, 0x3fe82ad000000000L, 0x3fe83bbe00000000L, 0x3fe84c9c00000000L,
            0x3fe85d6900000000L, 0x3fe86e2500000000L, 0x3fe87ed000000000L, 0x3fe88f6b00000000L,
            0x3fe89ff500000000L, 0x3fe8b06f00000000L, 0x3fe8c0d900000000L, 0x3fe8d13200000000L,
            0x3fe8e17a00000000L, 0x3fe8f1b300000000L, 0x3fe901db00000000L, 0x3fe911f300000000L,
            0x3fe921fb00000000L,
    };
    private static final long[] ATAN_LO = {
            0x3e56e59fbd38db2cL, 0x3e64e3aa54dedf96L, 0x3e67e105ab1bda88L, 0x3e48c5254d013fd0L,
            0x3e2cf8ab3ad62670L, 0x3e59dca4bec80468L, 0x3e53f4b5ec98a8daL, 0x3e6b9d49619d81feL,
            0x3e43017887460934L, 0x3e511e3eca0b9944L, 0x3e54f3f73c5a332eL, 0x3e5c71c8ae0e00a6L,
            0x3e67cde0f86fbdc7L, 0x3e570f328c889c72L, 0x3e5c07ae9b994efeL, 0x3e40c8021d7b1698L,
            0x3e635585edb8cb22L, 0x3e70842567b30e96L, 0x3e799e811031472eL, 0x3e6041821416bceeL,
            0x3e7f6086e4dc96f4L, 0x3e471a535c5f1b58L, 0x3e765f743fe63ca1L, 0x3e7dbd733472d014L,
            0x3e7d18cc4d8b0d1dL, 0x3e78c12553c8fb29L, 0x3e753b49e2e8f991L, 0x3e77422ae148c141L,
            0x3e4e3ec269df56a8L, 0x3e7ff6754e7e0ac9L, 0x3e7131267b1b5aadL, 0x3e7d14fa403a94bcL,
            0x3e62f396c089a3d8L, 0x3e7c731d78fa95bbL, 0x3e7c50f385177399L, 0x3e6f41409c6f2c20L,
            0x3e7d2d90c4c39ec0L, 0x3e680420696f2106L, 0x3e4b40327943a2e8L, 0x3e65d35e02f3d2a2L,
            0x3e64a498288117b0L, 0x3e635da119afb324L, 0x3e714e85cdb9a908L, 0x3e638754e5547b9aL,
            0x3e7be40ae6ce3246L, 0x3e70c993b3bea7e7L, 0x3e71d2dd89ac3359L, 0x3e61476603332c46L,
            0x3e7f25901bac55b7L, 0x3e7f881b7c826e28L, 0x3e7441996d698d20L, 0x3e8407ac521ea089L,
            0x3e82fb0c6c4b1723L, 0x3e8ca135966a3e18L, 0x3e6b1218e4d646e4L, 0x3e6d4e72a350d288L,
            0x3e84617e2f04c329L, 0x3e6096ec41e82650L, 0x3e79f91f25773e6eL, 0x3e659c0820f1d674L,
            0x3e602bf7a2df1064L, 0x3e8fb36bfc40508fL, 0x3e7ea08f3f8dc892L, 0x3e73ed6254656a0eL,
            0x3e6b83f5e5e69c58L, 0x3e8d6ec2af768592L, 0x3e6493889a226f94L, 0x3e85ad8fa65279baL,
            0x3e6b615784d45434L, 0x3e809a184368f145L, 0x3e761a2439b0d91cL, 0x3e7ce1a65e39a978L,
            0x3e832a39a93b6a66L, 0x3e81c3699af804e7L, 0x3e575e0f4e44ede8L, 0x3e8f77ced1a7a83bL,
            0x3e284e7f0cb1b500L, 0x3e8ec6b838b02dfeL, 0x3e83ebf4dfbeda87L, 0x3e89397aed9cb475L,
            0x3e707937bc239c54L, 0x3e8aa754553131b6L, 0x3e74a05d407c45dcL, 0x3e8132231a206dd0L,
            0x3e72d8ecfdd69c88L, 0x3e7a852c74218606L, 0x3e871bf2baeebb50L, 0x3e483d7db7491820L,
            0x3e6ca50d92b6da14L, 0x3e56f5cde8530298L, 0x3e7f343198910740L, 0x3e70e8d241ccd80aL,
            0x3e71535ac619e6c8L, 0x3e77316041c36cd2L, 0x3e7985a000637d8eL, 0x3e6f2f29858c0a68L,
            0x3e8879847f96d909L, 0x3e8ab3d319e12e42L, 0x3e75088162dfc4c2L, 0x3e605749a1cd9d8cL,
            0x3e5da65c6c6b8618L, 0x3e6739bf7df1ad64L, 0x3e6bc31252aa3340L, 0x3e5e528191ad3aa8L,
            0x3e8929d93df19f18L, 0x3e5ff11eb693a080L, 0x3e455ae3f145a3a0L, 0x3e7cbcd8c6c0ca82L,
            0x3e70cb04d425d304L, 0x3e79adfcab5be678L, 0x3e893d90c5662508L, 0x3e768489bd35ff40L,
            0x3e3586ed3da2b7e0L, 0x3e87604d2e850eeeL, 0x3e7ac1d12bfb53d8L, 0x3e39b3d468274740L,
            0x3e7fc5d68d10e53cL, 0x3e88f9e51884becbL, 0x3e8a87f0869c06d1L, 0x3e831e7279f685faL,
            0x3e46a8282f9719b0L, 0x3e60d2724a8a44e0L, 0x3e8a60524b11ad4eL, 0x3e575fdf832750f0L,
            0x3e8cf06902e4cd36L, 0x3e6e82422d4f6d10L, 0x3e524a091063e6c0L, 0x3e78a1a172dc6f38L,
            0x3e929b6619f8a92dL, 0x3e79274d9c1b70c8L, 0x3e50c34b1fbb7930L, 0x3e6639866c20eb50L,
            0x3e86d6d0f6832e9eL, 0x3e9af54def99f25eL, 0x3e916cfc52a00262L, 0x3e8dcc1e83569c32L,
            0x3e937f7a551ed425L, 0x3e9f6360adc98887L, 0x3e92c6ec8d35a2c1L, 0x3e8bd44df84cb036L,
            0x3e9117cf826e310eL, 0x3e9ca533f332cfc9L, 0x3e90f208509dbc2eL, 0x3e8cd07d93c945deL,
            0x3e957bdfd67e6d72L, 0x3e7aab89c516c658L, 0x3e63e823b1a1b8a0L, 0x3e8307464a9d6d3cL,
            0x3e9c5993cd438843L, 0x3e9ba2fca02ab554L, 0x3e801a5b6983a268L, 0x3e6273d1b350efc8L,
            0x3e864c238c37b0c6L, 0x3e6aded07370a300L, 0x3e878091197eb47eL, 0x3e74b0f245e0dabcL,
            0x3e9080d9794e2eafL, 0x3e8d4ec242b60c76L, 0x3e4221d2f940caa0L, 0x3e7cdbc42b2bba5cL,
            0x3e6cce37bb440840L, 0x3e96c1d999cf1dd0L, 0x3e5bed8a07eb0870L, 0x3e769ed88f490e3cL,
            0x3e6cd41719b73ef0L, 0x3e9cbc4ac95b41b7L, 0x3e9238f1b890f5d7L, 0x3e750c4282259cc4L,
            0x3e9713d2de87b3e2L, 0x3e81d5a7d2255276L, 0x3e9c0dfd48227ac1L, 0x3e91c964dab76753L,
            0x3e86de56d5704496L, 0x3e84aeb71fd19968L, 0x3e8fbf91c57b1918L, 0x3e9d6bef7fbe5d9aL,
            0x3e9464d3dc249066L, 0x3e9638e2ec4d9073L, 0x3e716f4a7247ea7cL, 0x3e31a0a740f1d440L,
            0x3e86edbb0114a33cL, 0x3e7dbee8bf1d513cL, 0x3e95b8bdb0248f73L, 0x3e97de3d3f5eac64L,
            0x3e8ee24187ae448aL, 0x3e9e06c591ec5192L, 0x3e74e3861a332738L, 0x3e7a9599dcc2bfe4L,
            0x3e6f732fbad43468L, 0x3e9eb9f573b727d9L, 0x3e98b212a2eb9897L, 0x3e9384884c167215L,
            0x3e90e2d363020051L, 0x3e92820879fbd022L, 0x3e9a1ab9893e4b30L, 0x3e82d1b817a24478L,
            0x3e615d7b8ded4878L, 0x3e78968f9db3a5e4L, 0x3e971c4171fe135fL, 0x3e96d80f605d0d8cL,
            0x3e7c91f043691590L, 0x3e839f8a15fce2b2L, 0x3e455beda9d94b80L, 0x3e8b12c15d60949aL,
            0x3e924167b312bfe3L, 0x3e90ab8633070277L, 0x3e854554ebbc80eeL, 0x3e60204aef5a4bb8L,
            0x3e98af08c679cf2cL, 0x3e90852a330ae6c8L, 0x3e86d3eb9ec32916L, 0x3e8685cb7fcbbafeL,
            0x3e91f751c1e0bd95L, 0x3e5705b1b0f72560L, 0x3e9b98d8d808ca92L, 0x3e62ea22c75cc980L,
            0x3e97aba62bca0350L, 0x3e9d73833442278cL, 0x3e95a5ca1fb18bf9L, 0x3e61a6092b6ecf28L,
            0x3e744fd049aac104L, 0x3e2c114fd8df5180L, 0x3e95972f130feae5L, 0x3e7ca034a55fe198L,
            0x3e96e2b149990227L, 0x3e7b00000294592cL, 0x3e98b9bdc442620eL, 0x3e8d94fdfabf3e4eL,
            0x3e85db30b145ad9aL, 0x3e8e3e1eb95022b0L, 0x3e9d5b8b45442bd6L, 0x3e97a046231ecd2eL,
            0x3e9feafe3ef55232L, 0x3e9839e7bfd78267L, 0x3e645cf49d6fa900L, 0x3e4be3132b27f380L,
            0x3e9533980bb84f9fL, 0x3e5889e2ce3ba390L, 0x3e7f7778c3ad0cc8L, 0x3e846660cec4eba2L,
            0x3e85110b4611a626L,
    };

    private static final long[] ATAN_F = {
            0x3faff55bb72cfde9L, 0x3fb0f99ea71d52a6L, 0x3fb1f86dbf082d58L, 0x3fb2f719318a4a9aL,
            0x3fb3f59f0e7c559dL, 0x3fb4f3fd677292fbL, 0x3fb5f2324fd2d7b2L, 0x3fb6f03bdcea4b0cL,
            0x3fb7ee182602f10eL, 0x3fb8ebc54478fb28L, 0x3fb9e94153cfdcf1L, 0x3fbae68a71c722b8L,
            0x3fbbe39ebe6f07c3L, 0x3fbce07c5c3cca32L, 0x3fbddd21701eba6eL, 0x3fbed98c2190043aL,
            0x3fbfd5ba9aac2f6dL, 0x3fc068d584212b3dL, 0x3fc0e6adccf40881L, 0x3fc1646541060850L,
            0x3fc1e1fafb043726L, 0x3fc25f6e171a535cL, 0x3fc2dcbdb2fba1ffL, 0x3fc359e8edeb99a3L,
            0x3fc3d6eee8c6626cL, 0x3fc453cec6092a9eL, 0x3fc4d087a9da4f17L, 0x3fc54d18ba11570aL,
            0x3fc5c9811e3ec269L, 0x3fc645bfffb3aa73L, 0x3fc6c1d4898933d8L, 0x3fc73dbde8a7d201L,
            0x3fc7b97b4bce5b02L, 0x3fc8350be398ebc7L, 0x3fc8b06ee2879c28L, 0x3fc92ba37d050271L,
            0x3fc9a6a8e96c8626L, 0x3fca217e601081a5L, 0x3fca9c231b403279L, 0x3fcb1696574d780bL,
            0x3fcb90d7529260a2L, 0x3fcc0ae54d768466L, 0x3fcc84bf8a742e6dL, 0x3fccfe654e1d5395L,
            0x3fcd77d5df205736L, 0x3fcdf110864c9d9dL, 0x3fce6a148e96ec4dL, 0x3fcee2e1451d980cL,
            0x3fcf5b75f92c80ddL, 0x3fcfd3d1fc40dbe4L, 0x3fd025fa510665b5L, 0x3fd061eea03d6290L,
            0x3fd09dc597d86362L, 0x3fd0d97ee509acb3L, 0x3fd1151a362431c9L, 0x3fd150973a9ce546L,
            0x3fd18bf5a30bf178L, 0x3fd1c735212dd883L, 0x3fd2025567e47c95L, 0x3fd23d562b381041L,
            0x3fd278372057ef45L, 0x3fd2b2f7fd9b5fe2L, 0x3fd2ed987a823cfeL, 0x3fd328184fb58951L,
            0x3fd362773707ebcbL, 0x3fd39cb4eb76157bL, 0x3fd3d6d129271134L, 0x3fd410cbad6c7d32L,
            0x3fd44aa436c2af09L, 0x3fd4845a84d0c21bL, 0x3fd4bdee586890e6L, 0x3fd4f75f73869978L,
            0x3fd530ad9951cd49L, 0x3fd569d88e1b4cd7L, 0x3fd5a2e0175e0f4eL, 0x3fd5dbc3fbbe768dL,
            0x3fd614840309cfe1L, 0x3fd64d1ff635c1c5L, 0x3fd685979f5fa6fdL, 0x3fd6bdeac9cbd76cL,
            0x3fd6f61941e4def0L, 0x3fd72e22d53aa2a9L, 0x3fd7660752817501L, 0x3fd79dc6899118d1L,
            0x3fd7d5604b63b3f7L, 0x3fd80cd46a14b1d0L, 0x3fd84422b8df95d7L, 0x3fd87b4b0c1ebedbL,
            0x3fd8b24d394a1b25L, 0x3fd8e92916f5cde8L, 0x3fd91fde7cd0c662L, 0x3fd9566d43a34907L,
            0x3fd98cd5454d6b18L, 0x3fd9c3165cc58107L, 0x3fd9f93066168001L, 0x3fda2f233e5e530bL,
            0x3fda64eec3cc23fcL, 0x3fda9a92d59e98cfL, 0x3fdad00f5422058bL, 0x3fdb056420ae9343L,
            0x3fdb3a911da65c6cL, 0x3fdb6f962e737efbL, 0x3fdba473378624a5L, 0x3fdbd9281e528191L,
            0x3fdc0db4c94ec9efL, 0x3fdc42191ff11eb6L, 0x3fdc76550aad71f8L, 0x3fdcaa6872f3631bL,
            0x3fdcde53432c1350L, 0x3fdd121566b7f2adL, 0x3fdd45aec9ec862bL, 0x3fdd791f5a1226f4L,
            0x3fddac670561bb4fL, 0x3fdddf85bb026974L, 0x3fde127b6b0744afL, 0x3fde4548066cf51aL,
            0x3fde77eb7f175a34L, 0x3fdeaa65c7cf28c4L, 0x3fdedcb6d43f8434L, 0x3fdf0ede98f393cfL,
            0x3fdf40dd0b541417L, 0x3fdf72b221a4e495L, 0x3fdfa45dd3029258L, 0x3fdfd5e0175fdf83L,
            0x3fe0039c73c1a40bL, 0x3fe01c341e82422dL, 0x3fe034b709250488L, 0x3fe04d25314342e5L,
            0x3fe0657e94db30cfL, 0x3fe07dc3324e9b38L, 0x3fe095f30861a58fL, 0x3fe0ae0e1639866cL,
            0x3fe0c6145b5b43daL, 0x3fe0de05d7aa6f7cL, 0x3fe0f5e28b67e295L, 0x3fe10daa77307a0dL,
            0x3fe1255d9bfbd2a8L, 0x3fe13cfbfb1b056eL, 0x3fe1548596376469L, 0x3fe16bfa6f5137e1L,
            0x3fe1835a88be7c13L, 0x3fe19aa5e5299f99L, 0x3fe1b1dc87904284L, 0x3fe1c8fe7341f64fL,
            0x3fe1e00babdefeb3L, 0x3fe1f7043557138aL, 0x3fe20de813e823b1L, 0x3fe224b74c1d192aL,
            0x3fe23b71e2cc9e6aL, 0x3fe25217dd17e501L, 0x3fe268a940696da6L, 0x3fe27f261273d1b3L,
            0x3fe2958e59308e30L, 0x3fe2abe21aded073L, 0x3fe2c2215e024465L, 0x3fe2d84c2961e48bL,
            0x3fe2ee628406cbcaL, 0x3fe30464753b090aL, 0x3fe31a52048874beL, 0x3fe3302b39b78856L,
            0x3fe345f01cce37bbL, 0x3fe35ba0b60eccceL, 0x3fe3713d0df6c503L, 0x3fe386c52d3db11eL,
            0x3fe39c391cd41719L, 0x3fe3b198e5e2564aL, 0x3fe3c6e491c78dc4L, 0x3fe3dc1c2a188504L,
            0x3fe3f13fb89e96f4L, 0x3fe4064f47569f48L, 0x3fe41b4ae06fea41L, 0x3fe430328e4b26d5L,
            0x3fe445065b795b55L, 0x3fe459c652badc7fL, 0x3fe46e727efe4715L, 0x3fe4830aeb5f7bfdL,
            0x3fe4978fa3269ee1L, 0x3fe4ac00b1c71762L, 0x3fe4c05e22de94e4L, 0x3fe4d4a8023414e8L,
            0x3fe4e8de5bb6ec04L, 0x3fe4fd013b7dd17eL, 0x3fe51110adc5ed81L, 0x3fe5250cbef1e9faL,
            0x3fe538f57b89061eL, 0x3fe54ccaf0362c8fL, 0x3fe5608d29c70c34L, 0x3fe5743c352b33b9L,
            0x3fe587d81f732fbaL, 0x3fe59b60f5cfab9dL, 0x3fe5aed6c5909517L, 0x3fe5c2399c244260L,
            0x3fe5d58987169b18L, 0x3fe5e8c6941043cfL, 0x3fe5fbf0d0d5cc49L, 0x3fe60f084b46e05eL,
            0x3fe6220d115d7b8dL, 0x3fe634ff312d1f3bL, 0x3fe647deb8e20b8fL, 0x3fe65aabb6c07b02L,
            0x3fe66d663923e086L, 0x3fe6800e4e7e2857L, 0x3fe692a40556fb6aL, 0x3fe6a5276c4b0575L,
            0x3fe6b798920b3d98L, 0x3fe6c9f7855c3198L, 0x3fe6dc44551553aeL, 0x3fe6ee7f10204aefL,
            0x3fe700a7c5784633L, 0x3fe712be84295198L, 0x3fe724c35b4fae7bL, 0x3fe736b65a172dffL,
            0x3fe748978fba8e0fL, 0x3fe75a670b82d8d8L, 0x3fe76c24dcc6c6c0L, 0x3fe77dd112ea22c7L,
            0x3fe78f6bbd5d315eL, 0x3fe7a0f4eb9c19a2L, 0x3fe7b26cad2e50fdL, 0x3fe7c3d311a6092bL,
            0x3fe7d528289fa093L, 0x3fe7e66c01c114fdL, 0x3fe7f79eacb97898L, 0x3fe808c03940694aL,
            0x3fe819d0b7158a4cL, 0x3fe82ad036000005L, 0x3fe83bbec5cdee22L, 0x3fe84c9c7653f7eaL,
            0x3fe85d69576cc2c5L, 0x3fe86e2578f87ae5L, 0x3fe87ed0eadc5a2aL, 0x3fe88f6bbd023118L,
            0x3fe89ff5ff57f1f7L, 0x3fe8b06fc1cf3dfeL, 0x3fe8c0d9145cf49dL, 0x3fe8d13206f8c4caL,
            0x3fe8e17aa99cc05dL, 0x3fe8f1b30c44f167L, 0x3fe901db3eeef187L, 0x3fe911f35199833bL,
            0x3fe921fb54442d18L,
    };

    private static double bits(long value) {
        return Double.longBitsToDouble(value);
    }

    private static long raw(double value) {
        return Double.doubleToRawLongBits(value);
    }

    private static int exponent(long value) {
        return (int) (value >>> 52) & 0x7ff;
    }

    private static long scaleUp(long value) {
        if ((value & INF) != 0) {
            return value + 0x4000000000000000L;
        }
        double biased = bits(value | 0x4010000000000000L);
        return raw(value < 0 ? biased + 4.0 : biased - 4.0);
    }

    private static long scaleDown(long value) {
        long sign = value & SIGN;
        long magnitude = value & ABS;
        int expo = (int) (magnitude >>> 52);
        if (expo > 100) {
            return sign | ((long) (expo - 100) << 52) | (magnitude & 0x800fffffffffffffL);
        }
        long mantissa = (magnitude & 0x801fffffffffffffL) | 0x10000000000000L;
        if (101 - expo > 54) {
            return sign;
        }
        long shifted = mantissa >>> (100 - expo);
        return sign | ((shifted >>> 1) + (shifted & 1));
    }

    private static float floatBits(int value) {
        return Float.intBitsToFloat(value);
    }

    static float atan2f(float yf, float xf, boolean fma3) {
        double large = xf;
        double small = yf;
        long xBits = raw(large);
        long yBits = raw(small);
        long xAbs = xBits & ABS;
        long yAbs = yBits & ABS;
        long ySign = yBits & SIGN;
        boolean xNegative = xBits < 0;
        int delta = exponent(yBits) - exponent(xBits);
        if (xAbs > INF) {
            return floatBits(Float.floatToRawIntBits(xf) | 0x400000);
        }
        if (yAbs > INF) {
            return floatBits(Float.floatToRawIntBits(yf) | 0x400000);
        }
        if (yAbs == 0) {
            if (!xNegative) {
                return (float) small;
            }
            return floatBits(ySign != 0 ? 0xc0490fdb : 0x40490fdb);
        }
        if (xAbs == 0 && ySign != 0) {
            return floatBits(0xbfc90fdb);
        }
        if (delta > 0x1a) {
            return floatBits(ySign != 0 ? 0xbfc90fdb : 0x3fc90fdb);
        }
        if (delta < -0xd && !xNegative) {
            if (delta < -150) {
                return ySign != 0 ? -0.0f : 0.0f;
            }
            if (delta < -0x7e) {
                return (float) bits(scaleDown(raw(C_2P100 * small / large)));
            }
            return (float) (small / large);
        }
        if (delta < -0x1a) {
            return floatBits(ySign != 0 ? 0xc0490fdb : 0x40490fdb);
        }
        if (yAbs == INF && xAbs == INF) {
            if (!xNegative) {
                return floatBits(ySign != 0 ? 0xbf490fdb : 0x3f490fdb);
            }
            return floatBits(ySign != 0 ? 0xc016cbe4 : 0x4016cbe4);
        }
        if (xNegative) {
            large = bits(raw(large) ^ SIGN);
        }
        if (ySign != 0) {
            small = bits(raw(small) ^ SIGN);
        }
        boolean swapped = small > large;
        if (swapped) {
            double held = large;
            large = small;
            small = held;
        }
        double u = small / large;
        double result;
        if (u > C_0_0625) {
            int index = fma3 ? (int) CrtMath.fma(C_256, u, C_HALF) : (int) (u * C_256 + C_HALF);
            double r;
            if (fma3) {
                double numerator = CrtMath.fma(small, C_256, -((double) index * large));
                r = numerator / CrtMath.fma(small, index, large * C_256);
                result = CrtMath.fma(-(r * r * r), R_B, r + bits(ATAN_F[index - 16]));
            } else {
                r = (small * C_256 - (double) index * large) / ((double) index * small + large * C_256);
                result = r + bits(ATAN_F[index - 16]) - r * r * r * R_B;
            }
        } else if (C_1E4 > u) {
            result = u;
        } else {
            double u2 = u * u;
            if (fma3) {
                double p = CrtMath.fma(-u2, S_C, S_D);
                p = CrtMath.fma(-u2, p, S_E);
                result = CrtMath.fma(-(u2 * u), p, u);
            } else {
                double p = S_D - u2 * S_C;
                p = S_E - p * u2;
                result = u - p * (u2 * u);
            }
        }
        if (swapped) {
            result = PIO2 - result;
        }
        if (xNegative) {
            result = PI - result;
        }
        if (ySign != 0) {
            result = -result;
        }
        return (float) result;
    }

    static double atan2(double y, double x, boolean fma3) {
        long xBits = raw(x);
        long yBits = raw(y);
        int xExpo = exponent(xBits);
        int yExpo = exponent(yBits);
        long xAbs = xBits & ABS;
        long yAbs = yBits & ABS;
        long xSign = xBits & SIGN;
        long ySign = yBits & SIGN;
        int delta = yExpo - xExpo;
        double small = y;
        double large = x;
        if (xAbs > INF) {
            return bits(xBits | QUIET);
        }
        if (yAbs > INF) {
            return bits(yBits | QUIET);
        }
        if (yAbs == 0) {
            if (xSign == 0) {
                return y;
            }
            return ySign != 0 ? -PI : PI;
        }
        if (xAbs == 0 && ySign != 0) {
            return -PIO2;
        }
        if (xExpo < 0x3fd && yExpo < 0x3fd) {
            long xScaled = scaleUp(xBits);
            long yScaled = scaleUp(yBits);
            small = bits(yScaled);
            large = bits(xScaled);
            delta = exponent(yScaled) - exponent(xScaled);
        }
        if (delta > 0x38) {
            return ySign != 0 ? -PIO2 : PIO2;
        }
        if (delta < -0x1c && xSign == 0) {
            if (delta < -1074) {
                return ySign != 0 ? -0.0 : 0.0;
            }
            if (delta < -1022) {
                return bits(scaleDown(raw(C_2P100 * small / large)));
            }
            return small / large;
        }
        if (delta < -0x38) {
            return ySign != 0 ? -PI : PI;
        }
        if (yAbs == INF && xAbs == INF) {
            if (xSign != 0) {
                return ySign != 0 ? -PI3O4 : PI3O4;
            }
            return ySign != 0 ? -PIO4 : PIO4;
        }
        if (xSign != 0) {
            large = bits(raw(large) ^ SIGN);
        }
        if (ySign != 0) {
            small = bits(raw(small) ^ SIGN);
        }
        boolean swapped = small > large;
        if (swapped) {
            double held = large;
            large = small;
            small = held;
        }
        double u = small / large;
        double head;
        double tail;
        if (u > C_0_0625) {
            int index = fma3 ? (int) CrtMath.fma(C_256, u, C_HALF) : (int) (u * C_256 + C_HALF);
            head = bits(ATAN_HI[index - 16]);
            double c = (double) index * C_1_256;
            int scale = 0x3ff - exponent(raw(large));
            int half = scale / 2;
            double first = bits((long) (half + 0x3ff) << 52);
            double second = bits((long) (scale - half + 0x3ff) << 52);
            double v = first * large * second;
            double w = first * small * second;
            double vh = bits(raw(v) & 0xfffffffff8000000L);
            if (fma3) {
                double numerator = CrtMath.fma(-vh, c, w);
                numerator = CrtMath.fma(-c, v - vh, numerator);
                double r = numerator / CrtMath.fma(w, c, v);
                double t = r + bits(ATAN_LO[index - 16]);
                double r2 = r * r;
                tail = CrtMath.fma(-r, CrtMath.fma(-r2, R_A, R_B) * r2, t);
            } else {
                double numerator = w - c * vh - (v - vh) * c;
                double r = numerator / (c * w + v);
                double t = r + bits(ATAN_LO[index - 16]);
                double r2 = r * r;
                tail = t - (R_B - r2 * R_A) * r2 * r;
            }
        } else if (C_1E8 > u) {
            head = 0.0;
            tail = u;
        } else {
            head = 0.0;
            double u2 = u * u;
            double vh = bits(raw(large) & 0xffffffff00000000L);
            double uh = bits(raw(u) & 0xffffffff00000000L);
            if (fma3) {
                double e = CrtMath.fma(-vh, uh, small);
                e = CrtMath.fma(-uh, large - vh, e);
                e = CrtMath.fma(-large, u - uh, e);
                double p = CrtMath.fma(-u2, S_A, S_B);
                p = CrtMath.fma(-u2, p, S_C);
                p = CrtMath.fma(-u2, p, S_D);
                p = CrtMath.fma(-u2, p, S_E);
                tail = CrtMath.fma(-(u2 * u), p, e / large) + u;
            } else {
                double e = small - uh * vh;
                e = e - (large - vh) * uh;
                e = e - (u - uh) * large;
                e = e / large;
                double p = S_B - u2 * S_A;
                p = S_C - p * u2;
                p = S_D - p * u2;
                p = S_E - p * u2;
                tail = u + (e - p * (u2 * u));
            }
        }
        if (swapped) {
            head = PIO2 - head;
            tail = PIO2_LO - tail;
        }
        if (xSign != 0) {
            head = PI_HI - head;
            tail = PI_LO - tail;
        }
        double result = head + tail;
        return ySign != 0 ? -result : result;
    }
}
