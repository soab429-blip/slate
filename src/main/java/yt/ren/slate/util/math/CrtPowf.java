package yt.ren.slate.util.math;

class CrtPowf {
    private static final long ABS = 0x7fffffffffffffffL;
    private static final int QUIET = 0x400000;
    private static final int INF = 0x7f800000;
    private static final int SIGN = 0x80000000;

    private static final double THIRD = bits(0x3fd5555555555555L);
    private static final double LN2 = bits(0x3fe62e42fefa39efL);
    private static final double OVERFLOW = bits(0x40562e4300000000L);
    private static final double UNDERFLOW = bits(0xc059d1da00000000L);
    private static final double SCALE = bits(0x40571547652b82feL);
    private static final double LN2_64 = bits(0x3f862e42fefa39efL);
    private static final double SIXTH = bits(0x3fc5555555555555L);
    private static final double NEAR = bits(0x3fb0000000000000L);
    private static final double A0 = bits(0x3f89999999bac6d4L);
    private static final double A1 = bits(0x3f3c8034c85dfff0L);
    private static final double B0 = bits(0x3fb55555555554e6L);
    private static final double B1 = bits(0x3f62492307f1519fL);

    private static final long[] INVERSE = {
            0x4000000000000000L, 0x3fffe01fe01fe020L, 0x3fffc07f01fc07f0L, 0x3fffa11caa01fa12L,
            0x3fff81f81f81f820L, 0x3fff6310aca0dbb5L, 0x3fff44659e4a4271L, 0x3fff25f644230ab5L,
            0x3fff07c1f07c1f08L, 0x3ffee9c7f8458e02L, 0x3ffecc07b301ecc0L, 0x3ffeae807aba01ebL,
            0x3ffe9131abf0b767L, 0x3ffe741aa59750e4L, 0x3ffe573ac901e574L, 0x3ffe3a9179dc1a73L,
            0x3ffe1e1e1e1e1e1eL, 0x3ffe01e01e01e01eL, 0x3ffde5d6e3f8868aL, 0x3ffdca01dca01dcaL,
            0x3ffdae6076b981dbL, 0x3ffd92f2231e7f8aL, 0x3ffd77b654b82c34L, 0x3ffd5cac807572b2L,
            0x3ffd41d41d41d41dL, 0x3ffd272ca3fc5b1aL, 0x3ffd0cb58f6ec074L, 0x3ffcf26e5c44bfc6L,
            0x3ffcd85689039b0bL, 0x3ffcbe6d9601cbe7L, 0x3ffca4b3055ee191L, 0x3ffc8b265afb8a42L,
            0x3ffc71c71c71c71cL, 0x3ffc5894d10d4986L, 0x3ffc3f8f01c3f8f0L, 0x3ffc26b5392ea01cL,
            0x3ffc0e070381c0e0L, 0x3ffbf583ee868d8bL, 0x3ffbdd2b899406f7L, 0x3ffbc4fd65883e7bL,
            0x3ffbacf914c1bad0L, 0x3ffb951e2b18ff23L, 0x3ffb7d6c3dda338bL, 0x3ffb65e2e3beee05L,
            0x3ffb4e81b4e81b4fL, 0x3ffb37484ad806ceL, 0x3ffb2036406c80d9L, 0x3ffb094b31d922a4L,
            0x3ffaf286bca1af28L, 0x3ffadbe87f94905eL, 0x3ffac5701ac5701bL, 0x3ffaaf1d2f87ebfdL,
            0x3ffa98ef606a63beL, 0x3ffa82e65130e159L, 0x3ffa6d01a6d01a6dL, 0x3ffa574107688a4aL,
            0x3ffa41a41a41a41aL, 0x3ffa2c2a87c51ca0L, 0x3ffa16d3f97a4b02L, 0x3ffa01a01a01a01aL,
            0x3ff9ec8e951033d9L, 0x3ff9d79f176b682dL, 0x3ff9c2d14ee4a102L, 0x3ff9ae24ea5510daL,
            0x3ff999999999999aL, 0x3ff9852f0d8ec0ffL, 0x3ff970e4f80cb872L, 0x3ff95cbb0be377aeL,
            0x3ff948b0fcd6e9e0L, 0x3ff934c67f9b2ce6L, 0x3ff920fb49d0e229L, 0x3ff90d4f120190d5L,
            0x3ff8f9c18f9c18faL, 0x3ff8e6527af1373fL, 0x3ff8d3018d3018d3L, 0x3ff8bfce8062ff3aL,
            0x3ff8acb90f6bf3aaL, 0x3ff899c0f601899cL, 0x3ff886e5f0abb04aL, 0x3ff87427bcc092b9L,
            0x3ff8618618618618L, 0x3ff84f00c2780614L, 0x3ff83c977ab2beddL, 0x3ff82a4a0182a4a0L,
            0x3ff8181818181818L, 0x3ff8060180601806L, 0x3ff7f405fd017f40L, 0x3ff7e225515a4f1dL,
            0x3ff7d05f417d05f4L, 0x3ff7beb3922e017cL, 0x3ff7ad2208e0ecc3L, 0x3ff79baa6bb6398bL,
            0x3ff78a4c8178a4c8L, 0x3ff77908119ac60dL, 0x3ff767dce434a9b1L, 0x3ff756cac201756dL,
            0x3ff745d1745d1746L, 0x3ff734f0c541fe8dL, 0x3ff724287f46debcL, 0x3ff713786d9c7c09L,
            0x3ff702e05c0b8170L, 0x3ff6f26016f26017L, 0x3ff6e1f76b4337c7L, 0x3ff6d1a62681c861L,
            0x3ff6c16c16c16c17L, 0x3ff6b1490aa31a3dL, 0x3ff6a13cd1537290L, 0x3ff691473a88d0c0L,
            0x3ff6816816816817L, 0x3ff6719f3601671aL, 0x3ff661ec6a5122f9L, 0x3ff6524f853b4aa3L,
            0x3ff642c8590b2164L, 0x3ff63356b88ac0deL, 0x3ff623fa77016240L, 0x3ff614b36831ae94L,
            0x3ff6058160581606L, 0x3ff5f66434292dfcL, 0x3ff5e75bb8d015e7L, 0x3ff5d867c3ece2a5L,
            0x3ff5c9882b931057L, 0x3ff5babcc647fa91L, 0x3ff5ac056b015ac0L, 0x3ff59d61f123ccaaL,
            0x3ff58ed2308158edL, 0x3ff5805601580560L, 0x3ff571ed3c506b3aL, 0x3ff56397ba7c52e2L,
            0x3ff5555555555555L, 0x3ff54725e6bb82feL, 0x3ff5390948f40febL, 0x3ff52aff56a8054bL,
            0x3ff51d07eae2f815L, 0x3ff50f22e111c4c5L, 0x3ff5015015015015L, 0x3ff4f38f62dd4c9bL,
            0x3ff4e5e0a72f0539L, 0x3ff4d843bedc2c4cL, 0x3ff4cab88725af6eL, 0x3ff4bd3edda68fe1L,
            0x3ff4afd6a052bf5bL, 0x3ff4a27fad76014aL, 0x3ff49539e3b2d067L, 0x3ff4880522014880L,
            0x3ff47ae147ae147bL, 0x3ff46dce34596066L, 0x3ff460cbc7f5cf9aL, 0x3ff453d9e2c776caL,
            0x3ff446f86562d9fbL, 0x3ff43a2730abee4dL, 0x3ff42d6625d51f87L, 0x3ff420b5265e5951L,
            0x3ff4141414141414L, 0x3ff40782d10e6566L, 0x3ff3fb013fb013fbL, 0x3ff3ee8f42a5af07L,
            0x3ff3e22cbce4a902L, 0x3ff3d5d991aa75c6L, 0x3ff3c995a47babe7L, 0x3ff3bd60d9232955L,
            0x3ff3b13b13b13b14L, 0x3ff3a524387ac822L, 0x3ff3991c2c187f63L, 0x3ff38d22d366088eL,
            0x3ff3813813813814L, 0x3ff3755bd1c945eeL, 0x3ff3698df3de0748L, 0x3ff35dce5f9f2af8L,
            0x3ff3521cfb2b78c1L, 0x3ff34679ace01346L, 0x3ff33ae45b57bcb2L, 0x3ff32f5ced6a1dfaL,
            0x3ff323e34a2b10bfL, 0x3ff3187758e9ebb6L, 0x3ff30d190130d190L, 0x3ff301c82ac40260L,
            0x3ff2f684bda12f68L, 0x3ff2eb4ea1fed14bL, 0x3ff2e025c04b8097L, 0x3ff2d50a012d50a0L,
            0x3ff2c9fb4d812ca0L, 0x3ff2bef98e5a3711L, 0x3ff2b404ad012b40L, 0x3ff2a91c92f3c105L,
            0x3ff29e4129e4129eL, 0x3ff293725bb804a5L, 0x3ff288b01288b013L, 0x3ff27dfa38a1ce4dL,
            0x3ff27350b8812735L, 0x3ff268b37cd60127L, 0x3ff25e22708092f1L, 0x3ff2539d7e9177b2L,
            0x3ff2492492492492L, 0x3ff23eb79717605bL, 0x3ff23456789abcdfL, 0x3ff22a0122a0122aL,
            0x3ff21fb78121fb78L, 0x3ff21579804855e6L, 0x3ff20b470c67c0d9L, 0x3ff2012012012012L,
            0x3ff1f7047dc11f70L, 0x3ff1ecf43c7fb84cL, 0x3ff1e2ef3b3fb874L, 0x3ff1d8f5672e4abdL,
            0x3ff1cf06ada2811dL, 0x3ff1c522fc1ce059L, 0x3ff1bb4a4046ed29L, 0x3ff1b17c67f2bae3L,
            0x3ff1a7b9611a7b96L, 0x3ff19e0119e0119eL, 0x3ff19453808ca29cL, 0x3ff18ab083902bdbL,
            0x3ff1811811811812L, 0x3ff1778a191bd684L, 0x3ff16e0689427379L, 0x3ff1648d50fc3201L,
            0x3ff15b1e5f75270dL, 0x3ff151b9a3fdd5c9L, 0x3ff1485f0e0acd3bL, 0x3ff13f0e8d344724L,
            0x3ff135c81135c811L, 0x3ff12c8b89edc0acL, 0x3ff12358e75d3033L, 0x3ff11a3019a74826L,
            0x3ff1111111111111L, 0x3ff107fbbe011080L, 0x3ff0fef010fef011L, 0x3ff0f5edfab325a2L,
            0x3ff0ecf56be69c90L, 0x3ff0e40655826011L, 0x3ff0db20a88f4696L, 0x3ff0d24456359e3aL,
            0x3ff0c9714fbcda3bL, 0x3ff0c0a7868b4171L, 0x3ff0b7e6ec259dc8L, 0x3ff0af2f722eecb5L,
            0x3ff0a6810a6810a7L, 0x3ff09ddba6af8360L, 0x3ff0953f39010954L, 0x3ff08cabb37565e2L,
            0x3ff0842108421084L, 0x3ff07b9f29b8eae2L, 0x3ff073260a47f7c6L, 0x3ff06ab59c7912fbL,
            0x3ff0624dd2f1a9fcL, 0x3ff059eea0727586L, 0x3ff05197f7d73404L, 0x3ff04949cc1664c5L,
            0x3ff0410410410410L, 0x3ff038c6b78247fcL, 0x3ff03091b51f5e1aL, 0x3ff02864fc7729e9L,
            0x3ff0204081020408L, 0x3ff0182436517a37L, 0x3ff0101010101010L, 0x3ff0080402010080L,
            0x3ff0000000000000L,
    };
    private static final long[] LOG = {
            0x0000000000000000L, 0x3f6ff00aa2b10bc0L, 0x3f7fe02a6b106789L, 0x3f87dc475f810a77L,
            0x3f8fc0a8b0fc03e4L, 0x3f93cea44346a575L, 0x3f97b91b07d5b11bL, 0x3f9b9fc027af9198L,
            0x3f9f829b0e783300L, 0x3fa1b0d98923d980L, 0x3fa39e87b9febd60L, 0x3fa58a5bafc8e4d5L,
            0x3fa77458f632dcfcL, 0x3fa95c830ec8e3ebL, 0x3fab42dd711971bfL, 0x3fad276b8adb0b52L,
            0x3faf0a30c01162a6L, 0x3fb075983598e471L, 0x3fb16536eea37ae1L, 0x3fb253f62f0a1417L,
            0x3fb341d7961bd1d1L, 0x3fb42edcbea646f0L, 0x3fb51b073f06183fL, 0x3fb60658a93750c4L,
            0x3fb6f0d28ae56b4cL, 0x3fb7da766d7b12cdL, 0x3fb8c345d6319b21L, 0x3fb9ab42462033adL,
            0x3fba926d3a4ad563L, 0x3fbb78c82bb0eda1L, 0x3fbc5e548f5bc743L, 0x3fbd4313d66cb35dL,
            0x3fbe27076e2af2e6L, 0x3fbf0a30c01162a6L, 0x3fbfec9131dbeabbL, 0x3fc0671512ca596eL,
            0x3fc0d77e7cd08e59L, 0x3fc14785846742acL, 0x3fc1b72ad52f67a0L, 0x3fc2266f190a5acbL,
            0x3fc29552f81ff523L, 0x3fc303d718e47fd3L, 0x3fc371fc201e8f74L, 0x3fc3dfc2b0ecc62aL,
            0x3fc44d2b6ccb7d1eL, 0x3fc4ba36f39a55e5L, 0x3fc526e5e3a1b438L, 0x3fc59338d9982086L,
            0x3fc5ff3070a793d4L, 0x3fc66acd4272ad51L, 0x3fc6d60fe719d21dL, 0x3fc740f8f54037a5L,
            0x3fc7ab890210d909L, 0x3fc815c0a14357ebL, 0x3fc87fa06520c911L, 0x3fc8e928de886d41L,
            0x3fc9525a9cf456b4L, 0x3fc9bb362e7dfb83L, 0x3fca23bc1fe2b563L, 0x3fca8becfc882f19L,
            0x3fcaf3c94e80bff3L, 0x3fcb5b519e8fb5a4L, 0x3fcbc286742d8cd6L, 0x3fcc2968558c18c1L,
            0x3fcc8ff7c79a9a22L, 0x3fccf6354e09c5dcL, 0x3fcd5c216b4fbb91L, 0x3fcdc1bca0abec7dL,
            0x3fce27076e2af2e6L, 0x3fce8c0252aa5a60L, 0x3fcef0adcbdc5936L, 0x3fcf550a564b7b37L,
            0x3fcfb9186d5e3e2bL, 0x3fd00e6c45ad501dL, 0x3fd0402594b4d041L, 0x3fd071b85fcd590dL,
            0x3fd0a324e27390e3L, 0x3fd0d46b579ab74bL, 0x3fd1058bf9ae4ad5L, 0x3fd136870293a8b0L,
            0x3fd1675cababa60eL, 0x3fd1980d2dd4236fL, 0x3fd1c898c16999fbL, 0x3fd1f8ff9e48a2f3L,
            0x3fd22941fbcf7966L, 0x3fd2596010df763aL, 0x3fd2895a13de86a3L, 0x3fd2b9303ab89d25L,
            0x3fd2e8e2bae11d31L, 0x3fd31871c9544185L, 0x3fd347dd9a987d55L, 0x3fd3772662bfd85bL,
            0x3fd3a64c556945eaL, 0x3fd3d54fa5c1f710L, 0x3fd404308686a7e4L, 0x3fd432ef2a04e814L,
            0x3fd4618bc21c5ec2L, 0x3fd49006804009d1L, 0x3fd4be5f957778a1L, 0x3fd4ec973260026aL,
            0x3fd51aad872df82dL, 0x3fd548a2c3add263L, 0x3fd5767717455a6cL, 0x3fd5a42ab0f4cfe2L,
            0x3fd5d1bdbf5809caL, 0x3fd5ff3070a793d4L, 0x3fd62c82f2b9c795L, 0x3fd659b57303e1f3L,
            0x3fd686c81e9b14afL, 0x3fd6b3bb2235943eL, 0x3fd6e08eaa2ba1e4L, 0x3fd70d42e2789236L,
            0x3fd739d7f6bbd007L, 0x3fd7664e1239dbcfL, 0x3fd792a55fdd47a2L, 0x3fd7bede0a37afc0L,
            0x3fd7eaf83b82afc3L, 0x3fd816f41da0d496L, 0x3fd842d1da1e8b17L, 0x3fd86e919a330ba0L,
            0x3fd89a3386c1425bL, 0x3fd8c5b7c858b48bL, 0x3fd8f11e873662c7L, 0x3fd91c67eb45a83eL,
            0x3fd947941c2116fbL, 0x3fd972a341135158L, 0x3fd99d958117e08bL, 0x3fd9c86b02dc0863L,
            0x3fd9f323ecbf984cL, 0x3fda1dc064d5b995L, 0x3fda484090e5bb0aL, 0x3fda72a4966bd9eaL,
            0x3fda9cec9a9a084aL, 0x3fdac718c258b0e4L, 0x3fdaf1293247786bL, 0x3fdb1b1e0ebdfc5bL,
            0x3fdb44f77bcc8f63L, 0x3fdb6eb59d3cf35eL, 0x3fdb9858969310fbL, 0x3fdbc1e08b0dad0aL,
            0x3fdbeb4d9da71b7cL, 0x3fdc149ff115f027L, 0x3fdc3dd7a7cdad4dL, 0x3fdc66f4e3ff6ff8L,
            0x3fdc8ff7c79a9a22L, 0x3fdcb8e0744d7acaL, 0x3fdce1af0b85f3ebL, 0x3fdd0a63ae721e64L,
            0x3fdd32fe7e00ebd5L, 0x3fdd5b7f9ae2c684L, 0x3fdd83e7258a2f3eL, 0x3fddac353e2c5954L,
            0x3fddd46a04c1c4a1L, 0x3fddfc859906d5b5L, 0x3fde24881a7c6c26L, 0x3fde4c71a8687704L,
            0x3fde744261d68788L, 0x3fde9bfa659861f5L, 0x3fdec399d2468cc0L, 0x3fdeeb20c640ddf4L,
            0x3fdf128f5faf06edL, 0x3fdf39e5bc811e5cL, 0x3fdf6123fa7028acL, 0x3fdf884a36fe9ec2L,
            0x3fdfaf588f78f31fL, 0x3fdfd64f20f61572L, 0x3fdffd2e0857f498L, 0x3fe011fab125ff8aL,
            0x3fe02552a5a5d0ffL, 0x3fe0389eefce633bL, 0x3fe04bdf9da926d2L, 0x3fe05f14bd26459cL,
            0x3fe0723e5c1cdf40L, 0x3fe0855c884b450eL, 0x3fe0986f4f573521L, 0x3fe0ab76bece14d2L,
            0x3fe0be72e4252a83L, 0x3fe0d163ccb9d6b8L, 0x3fe0e44985d1cc8cL, 0x3fe0f7241c9b497dL,
            0x3fe109f39e2d4c97L, 0x3fe11cb81787ccf8L, 0x3fe12f719593efbcL, 0x3fe1422025243d45L,
            0x3fe154c3d2f4d5eaL, 0x3fe1675cababa60eL, 0x3fe179eabbd899a1L, 0x3fe18c6e0ff5cf06L,
            0x3fe19ee6b467c96fL, 0x3fe1b154b57da29fL, 0x3fe1c3b81f713c25L, 0x3fe1d610fe677003L,
            0x3fe1e85f5e7040d0L, 0x3fe1faa34b87094cL, 0x3fe20cdcd192ab6eL, 0x3fe21f0bfc65beecL,
            0x3fe23130d7bebf43L, 0x3fe2434b6f483934L, 0x3fe2555bce98f7cbL, 0x3fe26762013430e0L,
            0x3fe2795e1289b11bL, 0x3fe28b500df60783L, 0x3fe29d37fec2b08bL, 0x3fe2af15f02640adL,
            0x3fe2c0e9ed448e8cL, 0x3fe2d2b4012edc9eL, 0x3fe2e47436e40268L, 0x3fe2f62a99509546L,
            0x3fe307d7334f10beL, 0x3fe3197a0fa7fe6aL, 0x3fe32b1339121d71L, 0x3fe33ca2ba328995L,
            0x3fe34e289d9ce1d3L, 0x3fe35fa4edd36ea0L, 0x3fe37117b54747b6L, 0x3fe38280fe58797fL,
            0x3fe393e0d3562a1aL, 0x3fe3a5373e7ebdfaL, 0x3fe3b68449fffc23L, 0x3fe3c7c7fff73206L,
            0x3fe3d9026a7156fbL, 0x3fe3ea33936b2f5cL, 0x3fe3fb5b84d16f42L, 0x3fe40c7a4880dce9L,
            0x3fe41d8fe84672aeL, 0x3fe42e9c6ddf80bfL, 0x3fe43f9fe2f9ce67L, 0x3fe4509a5133bb0aL,
            0x3fe4618bc21c5ec2L, 0x3fe472743f33aaadL, 0x3fe48353d1ea88dfL, 0x3fe4942a83a2fc07L,
            0x3fe4a4f85db03ebbL, 0x3fe4b5bd6956e274L, 0x3fe4c679afccee3aL, 0x3fe4d72d3a39fd00L,
            0x3fe4e7d811b75bb1L, 0x3fe4f87a3f5026e9L, 0x3fe50913cc01686bL, 0x3fe519a4c0ba3446L,
            0x3fe52a2d265bc5abL, 0x3fe53aad05b99b7dL, 0x3fe54b2467999498L, 0x3fe55b9354b40bcdL,
            0x3fe56bf9d5b3f399L, 0x3fe57c57f336f191L, 0x3fe58cadb5cd7989L, 0x3fe59cfb25fae87eL,
            0x3fe5ad404c359f2dL, 0x3fe5bd7d30e71c73L, 0x3fe5cdb1dc6c1765L, 0x3fe5ddde57149923L,
            0x3fe5ee02a9241675L, 0x3fe5fe1edad18919L, 0x3fe60e32f44788d9L, 0x3fe61e3efda46467L,
            0x3fe62e42fefa39efL,
    };
    private static final long[] EXP = {
            0x3ff0000000000000L, 0x3ff02c9a3e778061L, 0x3ff059b0d3158574L, 0x3ff0874518759bc8L,
            0x3ff0b5586cf9890fL, 0x3ff0e3ec32d3d1a2L, 0x3ff11301d0125b51L, 0x3ff1429aaea92de0L,
            0x3ff172b83c7d517bL, 0x3ff1a35beb6fcb75L, 0x3ff1d4873168b9aaL, 0x3ff2063b88628cd6L,
            0x3ff2387a6e756238L, 0x3ff26b4565e27cddL, 0x3ff29e9df51fdee1L, 0x3ff2d285a6e4030bL,
            0x3ff306fe0a31b715L, 0x3ff33c08b26416ffL, 0x3ff371a7373aa9cbL, 0x3ff3a7db34e59ff7L,
            0x3ff3dea64c123422L, 0x3ff4160a21f72e2aL, 0x3ff44e086061892dL, 0x3ff486a2b5c13cd0L,
            0x3ff4bfdad5362a27L, 0x3ff4f9b2769d2ca7L, 0x3ff5342b569d4f82L, 0x3ff56f4736b527daL,
            0x3ff5ab07dd485429L, 0x3ff5e76f15ad2148L, 0x3ff6247eb03a5585L, 0x3ff6623882552225L,
            0x3ff6a09e667f3bcdL, 0x3ff6dfb23c651a2fL, 0x3ff71f75e8ec5f74L, 0x3ff75feb564267c9L,
            0x3ff7a11473eb0187L, 0x3ff7e2f336cf4e62L, 0x3ff82589994cce13L, 0x3ff868d99b4492edL,
            0x3ff8ace5422aa0dbL, 0x3ff8f1ae99157736L, 0x3ff93737b0cdc5e5L, 0x3ff97d829fde4e50L,
            0x3ff9c49182a3f090L, 0x3ffa0c667b5de565L, 0x3ffa5503b23e255dL, 0x3ffa9e6b5579fdbfL,
            0x3ffae89f995ad3adL, 0x3ffb33a2b84f15fbL, 0x3ffb7f76f2fb5e47L, 0x3ffbcc1e904bc1d2L,
            0x3ffc199bdd85529cL, 0x3ffc67f12e57d14bL, 0x3ffcb720dcef9069L, 0x3ffd072d4a07897cL,
            0x3ffd5818dcfba487L, 0x3ffda9e603db3285L, 0x3ffdfc97337b9b5fL, 0x3ffe502ee78b3ff6L,
            0x3ffea4afa2a490daL, 0x3ffefa1bee615a27L, 0x3fff50765b6e4540L, 0x3fffa7c1819e90d8L,
    };

    private static double bits(long value) {
        return Double.longBitsToDouble(value);
    }

    private static long raw(double value) {
        return Double.doubleToRawLongBits(value);
    }

    private static float floatBits(int value) {
        return Float.intBitsToFloat(value);
    }

    private static float error(int value, int code) {
        if (code >= 3 && code <= 5) {
            return floatBits(value | QUIET);
        }
        return floatBits(value);
    }

    private static float rint(float value) {
        return (float) Math.rint(value);
    }

    private static int oddSign(int yAbs, float y, int signSource) {
        if ((yAbs & 0x7f800000) > 0x4b000000) {
            return 0;
        }
        float rounded = rint(y);
        if (rounded != y) {
            return 0;
        }
        if ((((int) rounded) & 1) == 0) {
            return 0;
        }
        return signSource & SIGN;
    }

    static float powf(float x, float y, boolean avx) {
        int yBits = Float.floatToRawIntBits(y);
        int xBits = Float.floatToRawIntBits(x);
        int yAbs = yBits & 0x7fffffff;
        int xAbs = xBits & 0x7fffffff;
        int sign = 0;
        if (yAbs >= INF) {
            if (yAbs > INF) {
                if (xAbs > INF) {
                    return error(xBits == 0xffc00000 ? yBits : xBits, 5);
                }
                if (xBits == 0x3f800000) {
                    return yAbs >= 0x7fc00000 ? floatBits(0x3f800000) : error(0x3f800000, 4);
                }
                return error(yBits, 4);
            }
            if (xAbs > INF) {
                return error(xBits, 3);
            }
            if (xAbs == 0x3f800000) {
                return floatBits(0x3f800000);
            }
            if (yBits >= 0) {
                return floatBits(Integer.compareUnsigned(xAbs, 0x3f800000) < 0 ? 0 : INF);
            }
            if (xAbs == 0) {
                return floatBits(INF);
            }
            return floatBits(Integer.compareUnsigned(xAbs, 0x3f800000) < 0 ? INF : 0);
        }
        if (yAbs <= 0x3f800000) {
            if (yAbs == 0) {
                if (xAbs <= INF || xAbs >= 0x7fc00000) {
                    return floatBits(0x3f800000);
                }
                return error(xAbs, 3);
            }
            if (yBits == 0x3f800000) {
                if (xAbs > INF) {
                    return error(xBits, 3);
                }
                return x;
            }
        }
        if (xAbs >= INF) {
            if (xBits == INF) {
                return floatBits(yBits < 0 ? 0 : INF);
            }
            if (xBits == 0xff800000) {
                return floatBits(oddSign(yAbs, y, SIGN) | (yBits < 0 ? 0 : INF));
            }
            return error(xBits, 3);
        }
        if (xBits < 0x3f880000) {
            if (xBits <= 0) {
                if (xAbs == 0) {
                    int value = oddSign(yAbs, y, xBits) | (yBits < 0 ? INF : 0);
                    return yBits < 0 ? error(value, 2) : floatBits(value);
                }
                if ((yAbs & 0x7f800000) <= 0x4b000000) {
                    float rounded = rint(y);
                    if (rounded != y) {
                        return error(0xffc00000, 6);
                    }
                    if ((((int) rounded) & 1) != 0) {
                        sign = SIGN;
                    }
                }
            }
            double f = (double) x - 1.0;
            if ((raw(f) & ABS) < raw(NEAR)) {
                double s = f / (f + 2.0);
                double sf = s * f;
                double u = s + s;
                double v = u * u;
                double a0 = v * A0 + B0;
                double a1 = v * A1 + B1;
                double u3 = u * v;
                double u7 = v * v * u3;
                double sum = u3 * a0 + u7 * a1;
                sum = sum - sf;
                return exp((double) y * (f + sum), sign, false);
            }
        }
        long xRaw = raw(x);
        long mantissa = xRaw & 0x000fffffffffffffL;
        int index = (int) (mantissa >>> 44) + (int) ((mantissa >>> 43) & 1);
        double nearest = bits((long) (index | 0x3fe00) << 44);
        double scaled = bits(mantissa | 0x3fe0000000000000L);
        double exponent = (int) (((xRaw & 0x7ff0000000000000L) >>> 52) - 0x3ff);
        double r = (nearest - scaled) * bits(INVERSE[index]);
        double polynomial;
        if (avx) {
            double a = CrtMath.fma(r, THIRD, 0.5);
            a = CrtMath.fma(r, a, 1.0);
            polynomial = r * a;
        } else {
            double a = THIRD * r;
            double r2 = r * r;
            a = a + 0.5;
            polynomial = r2 * a + r;
        }
        double log = exponent * LN2 + bits(LOG[index]) - polynomial;
        return exp((double) y * log, sign, avx);
    }

    private static float exp(double t, int sign, boolean avx) {
        if (t > OVERFLOW) {
            return error(INF | sign, 9);
        }
        if (!(t > UNDERFLOW)) {
            return error(sign, 7);
        }
        int n = (int) Math.rint(t * SCALE);
        double table = bits(EXP[n & 0x3f]);
        double q;
        if (avx) {
            double rr = CrtMath.fma(-(double) n, LN2_64, t);
            double a = CrtMath.fma(rr, SIXTH, 0.5);
            a = CrtMath.fma(rr, a, 1.0);
            q = CrtMath.fma(rr * a, table, table);
        } else {
            double rr = t - (double) n * LN2_64;
            double a = SIXTH * rr;
            double r2 = rr * rr;
            a = a + 0.5;
            q = (r2 * a + rr) * table + table;
        }
        double result = bits(raw(q) + ((long) (n >> 6) << 52));
        return floatBits(Float.floatToRawIntBits((float) result) | sign);
    }
}
