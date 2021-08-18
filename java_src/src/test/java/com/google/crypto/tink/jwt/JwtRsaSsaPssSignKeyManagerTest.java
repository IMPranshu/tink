// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////
package com.google.crypto.tink.jwt;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;

import com.google.crypto.tink.CleartextKeysetHandle;
import com.google.crypto.tink.KeyTemplate;
import com.google.crypto.tink.KeyTemplates;
import com.google.crypto.tink.KeyTypeManager;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.proto.JwtRsaSsaPssAlgorithm;
import com.google.crypto.tink.proto.JwtRsaSsaPssKeyFormat;
import com.google.crypto.tink.proto.JwtRsaSsaPssPrivateKey;
import com.google.crypto.tink.proto.JwtRsaSsaPssPublicKey;
import com.google.crypto.tink.proto.JwtRsaSsaPssPublicKey.CustomKid;
import com.google.crypto.tink.proto.KeyData;
import com.google.crypto.tink.proto.KeyData.KeyMaterialType;
import com.google.crypto.tink.proto.Keyset;
import com.google.crypto.tink.subtle.Base64;
import com.google.crypto.tink.subtle.EngineFactory;
import com.google.crypto.tink.subtle.Enums;
import com.google.crypto.tink.subtle.Random;
import com.google.crypto.tink.subtle.RsaSsaPssSignJce;
import com.google.crypto.tink.testing.TestUtil;
import com.google.gson.JsonObject;
import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistryLite;
import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.RSAKeyGenParameterSpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.util.Set;
import java.util.TreeSet;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for JwtRsaSsaPssSignKeyManager. */
@RunWith(JUnitParamsRunner.class)
public class JwtRsaSsaPssSignKeyManagerTest {
  private final JwtRsaSsaPssSignKeyManager manager = new JwtRsaSsaPssSignKeyManager();
  private final KeyTypeManager.KeyFactory<JwtRsaSsaPssKeyFormat, JwtRsaSsaPssPrivateKey> factory =
      manager.keyFactory();

  @BeforeClass
  public static void setUp() throws Exception {
    JwtSignatureConfig.register();
  }

  private static JwtRsaSsaPssKeyFormat createKeyFormat(
      JwtRsaSsaPssAlgorithm algorithm, int modulusSizeInBits, BigInteger publicExponent) {
    return JwtRsaSsaPssKeyFormat.newBuilder()
        .setAlgorithm(algorithm)
        .setModulusSizeInBits(modulusSizeInBits)
        .setPublicExponent(ByteString.copyFrom(publicExponent.toByteArray()))
        .build();
  }

  private static Object[] parametersAlgoAndSize() {
    return new Object[] {
      new Object[] {JwtRsaSsaPssAlgorithm.PS256, 2048},
      new Object[] {JwtRsaSsaPssAlgorithm.PS256, 3072},
      new Object[] {JwtRsaSsaPssAlgorithm.PS256, 4096},
      new Object[] {JwtRsaSsaPssAlgorithm.PS384, 2048},
      new Object[] {JwtRsaSsaPssAlgorithm.PS384, 3072},
      new Object[] {JwtRsaSsaPssAlgorithm.PS384, 4096},
      new Object[] {JwtRsaSsaPssAlgorithm.PS512, 2048},
      new Object[] {JwtRsaSsaPssAlgorithm.PS512, 3072},
      new Object[] {JwtRsaSsaPssAlgorithm.PS512, 4096},
    };
  }

  private static Object[] templates() {
    return new Object[] {
      "JWT_PS256_2048_F4",
      "JWT_PS256_3072_F4",
      "JWT_PS384_3072_F4",
      "JWT_PS512_4096_F4",
      "JWT_PS256_2048_F4_RAW",
    };
  }

  @Test
  public void basics() throws Exception {
    assertThat(manager.getKeyType())
        .isEqualTo("type.googleapis.com/google.crypto.tink.JwtRsaSsaPssPrivateKey");
    assertThat(manager.getVersion()).isEqualTo(0);
    assertThat(manager.keyMaterialType()).isEqualTo(KeyMaterialType.ASYMMETRIC_PRIVATE);
  }

  @Test
  public void validateKeyFormat_empty_throw() throws Exception {
    assertThrows(
        GeneralSecurityException.class,
        () -> factory.validateKeyFormat(JwtRsaSsaPssKeyFormat.getDefaultInstance()));
  }

  @Test
  @Parameters(method = "parametersAlgoAndSize")
  public void validateKeyFormat_ok(JwtRsaSsaPssAlgorithm algorithm, int keySize)
      throws GeneralSecurityException {
    JwtRsaSsaPssKeyFormat format = createKeyFormat(algorithm, keySize, RSAKeyGenParameterSpec.F4);
    factory.validateKeyFormat(format);
  }

  @Test
  public void testKeyFormatsAreValid() throws Exception {
    for (KeyTypeManager.KeyFactory.KeyFormat<JwtRsaSsaPssKeyFormat> format :
        factory.keyFormats().values()) {
      factory.validateKeyFormat(format.keyFormat);
    }
  }

  @Test
  public void invalidKeyFormat_smallKey_throw()
      throws GeneralSecurityException {
    JwtRsaSsaPssKeyFormat format =
        createKeyFormat(JwtRsaSsaPssAlgorithm.PS256, 2047, RSAKeyGenParameterSpec.F4);
    assertThrows(GeneralSecurityException.class, () -> factory.validateKeyFormat(format));
  }

  private static Object[] parametersSmallPublicExponents() {
    return new Object[] {
      new Object[] {JwtRsaSsaPssAlgorithm.PS256, 3072},
      new Object[] {JwtRsaSsaPssAlgorithm.PS256, 4096},
      new Object[] {JwtRsaSsaPssAlgorithm.PS384, 2048},
      new Object[] {JwtRsaSsaPssAlgorithm.PS384, 3072},
      new Object[] {JwtRsaSsaPssAlgorithm.PS384, 4096},
      new Object[] {JwtRsaSsaPssAlgorithm.PS512, 2048},
      new Object[] {JwtRsaSsaPssAlgorithm.PS512, 3072},
      new Object[] {JwtRsaSsaPssAlgorithm.PS512, 4096},
    };
  }

  @Test
  @Parameters(method = "parametersSmallPublicExponents")
  public void invalidKeyFormat_smallPublicExponents_throw(
      JwtRsaSsaPssAlgorithm algorithm, int keySize) throws GeneralSecurityException {
    JwtRsaSsaPssKeyFormat format =
        createKeyFormat(algorithm, keySize, RSAKeyGenParameterSpec.F4.subtract(BigInteger.ONE));
    assertThrows(GeneralSecurityException.class, () -> factory.validateKeyFormat(format));
  }

  private static void checkConsistency(
      JwtRsaSsaPssPrivateKey privateKey, JwtRsaSsaPssKeyFormat keyFormat) {
    assertThat(privateKey.getPublicKey().getAlgorithm()).isEqualTo(keyFormat.getAlgorithm());
    assertThat(privateKey.getPublicKey().getE()).isEqualTo(keyFormat.getPublicExponent());
    assertThat(privateKey.getPublicKey().getN().toByteArray().length)
        .isGreaterThan(keyFormat.getModulusSizeInBits() / 8);
  }

  private static void checkKey(JwtRsaSsaPssPrivateKey privateKey) throws Exception {
    JwtRsaSsaPssPublicKey publicKey = privateKey.getPublicKey();
    assertThat(privateKey.getVersion()).isEqualTo(0);
    assertThat(publicKey.getVersion()).isEqualTo(privateKey.getVersion());
    BigInteger p = new BigInteger(1, privateKey.getP().toByteArray());
    BigInteger q = new BigInteger(1, privateKey.getQ().toByteArray());
    BigInteger n = new BigInteger(1, privateKey.getPublicKey().getN().toByteArray());
    BigInteger d = new BigInteger(1, privateKey.getD().toByteArray());
    BigInteger dp = new BigInteger(1, privateKey.getDp().toByteArray());
    BigInteger dq = new BigInteger(1, privateKey.getDq().toByteArray());
    BigInteger crt = new BigInteger(1, privateKey.getCrt().toByteArray());
    assertThat(p).isGreaterThan(BigInteger.ONE);
    assertThat(q).isGreaterThan(BigInteger.ONE);
    assertEquals(n, p.multiply(q));
    assertEquals(dp, d.mod(p.subtract(BigInteger.ONE)));
    assertEquals(dq, d.mod(q.subtract(BigInteger.ONE)));
    assertEquals(crt, q.modInverse(p));
  }

  @Test
  @Parameters(method = "parametersAlgoAndSize")
  public void createKeys_ok(JwtRsaSsaPssAlgorithm algorithm, int keySize) throws Exception {
    assumeFalse(TestUtil.isTsan());  // creating keys is too slow in Tsan.
    JwtRsaSsaPssKeyFormat format = createKeyFormat(algorithm, keySize, RSAKeyGenParameterSpec.F4);
    JwtRsaSsaPssPrivateKey key = factory.createKey(format);
    checkConsistency(key, format);
    checkKey(key);
  }

  @Test
  @Parameters(method = "parametersAlgoAndSize")
  public void createKey_alwaysNewElement_ok(JwtRsaSsaPssAlgorithm algorithm, int keySize)
      throws Exception {
    assumeFalse(TestUtil.isTsan());  // creating keys is too slow in Tsan.
    JwtRsaSsaPssKeyFormat format = createKeyFormat(algorithm, keySize, RSAKeyGenParameterSpec.F4);
    Set<String> keys = new TreeSet<>();
    // Calls newKey multiple times and make sure that they generate different keys -- takes about a
    // second per key.
    int numTests = 5;
    for (int i = 0; i < numTests; i++) {
      JwtRsaSsaPssPrivateKey key = factory.createKey(format);
      keys.add(TestUtil.hexEncode(key.getQ().toByteArray()));
      keys.add(TestUtil.hexEncode(key.getP().toByteArray()));
    }
    assertThat(keys).hasSize(2 * numTests);
  }

  @Test
  @Parameters(method = "parametersAlgoAndSize")
  public void createCorruptedModulusPrimitive_throws(JwtRsaSsaPssAlgorithm algorithm, int keySize)
      throws Exception {
    assumeFalse(TestUtil.isTsan());  // creating keys is too slow in Tsan.
    JwtRsaSsaPssKeyFormat format = createKeyFormat(algorithm, keySize, RSAKeyGenParameterSpec.F4);
    JwtRsaSsaPssPrivateKey originalKey = factory.createKey(format);
    byte[] originalN = originalKey.getPublicKey().getN().toByteArray();
    originalN[0] = (byte) (originalN[0] ^ 0x01);
    ByteString corruptedN = ByteString.copyFrom(originalN);
    JwtRsaSsaPssPublicKey corruptedPub =
        JwtRsaSsaPssPublicKey.newBuilder()
            .setVersion(originalKey.getPublicKey().getVersion())
            .setN(corruptedN)
            .setE(originalKey.getPublicKey().getE())
            .build();

    JwtRsaSsaPssPrivateKey corruptedKey =
        JwtRsaSsaPssPrivateKey.newBuilder()
            .setVersion(originalKey.getVersion())
            .setPublicKey(corruptedPub)
            .setD(originalKey.getD())
            .setP(originalKey.getP())
            .setQ(originalKey.getQ())
            .setDp(originalKey.getDp())
            .setDq(originalKey.getDq())
            .setCrt(originalKey.getCrt())
            .build();
    assertThrows(
        GeneralSecurityException.class,
        () -> manager.getPrimitive(corruptedKey, JwtPublicKeySignInternal.class));
  }

  @Test
  public void testDeriveKey_throw() throws Exception {
    assertThrows(
        UnsupportedOperationException.class,
        () ->
            factory.deriveKey(
                JwtRsaSsaPssKeyFormat.getDefaultInstance(),
                new ByteArrayInputStream(Random.randBytes(100))));
  }

  private static void checkTemplate(
      KeyTemplate template, JwtRsaSsaPssAlgorithm algorithm, int moduloSize, int publicExponent)
      throws Exception {
    assertThat(template.getTypeUrl()).isEqualTo(new JwtRsaSsaPssSignKeyManager().getKeyType());
    JwtRsaSsaPssKeyFormat format =
        JwtRsaSsaPssKeyFormat.parseFrom(
            template.getValue(), ExtensionRegistryLite.getEmptyRegistry());
    assertThat(format.getAlgorithm()).isEqualTo(algorithm);
    assertThat(format.getModulusSizeInBits()).isEqualTo(moduloSize);
    assertThat(new BigInteger(1, format.getPublicExponent().toByteArray()))
        .isEqualTo(BigInteger.valueOf(publicExponent));
  }

  @Test
  public void testJwtRsa2048AlgoRS256F4Template_ok() throws Exception {
    checkTemplate(KeyTemplates.get("JWT_PS256_2048_F4"), JwtRsaSsaPssAlgorithm.PS256, 2048, 65537);
    checkTemplate(
        KeyTemplates.get("JWT_PS256_2048_F4_RAW"), JwtRsaSsaPssAlgorithm.PS256, 2048, 65537);
  }

  @Test
  public void testJwtRsa4096AlgoRS512F4Template_ok() throws Exception {
    checkTemplate(KeyTemplates.get("JWT_PS512_4096_F4"), JwtRsaSsaPssAlgorithm.PS512, 4096, 65537);
    checkTemplate(
        KeyTemplates.get("JWT_PS512_4096_F4_RAW"), JwtRsaSsaPssAlgorithm.PS512, 4096, 65537);
  }

  @Test
  public void testJwtRsa3072AlgoRS384F4Template_ok() throws Exception {
    checkTemplate(KeyTemplates.get("JWT_PS384_3072_F4"), JwtRsaSsaPssAlgorithm.PS384, 3072, 65537);
    checkTemplate(
        KeyTemplates.get("JWT_PS384_3072_F4_RAW"), JwtRsaSsaPssAlgorithm.PS384, 3072, 65537);
  }

  @Test
  public void testJwtRsa3072AlgoRS256F4Template_ok() throws Exception {
    checkTemplate(KeyTemplates.get("JWT_PS256_3072_F4"), JwtRsaSsaPssAlgorithm.PS256, 3072, 65537);
    checkTemplate(
        KeyTemplates.get("JWT_PS256_3072_F4_RAW"), JwtRsaSsaPssAlgorithm.PS256, 3072, 65537);
  }

  @Test
  public void testTinkTemplatesAreTink() throws Exception {
    assertThat(KeyTemplates.get("JWT_PS256_2048_F4").getOutputPrefixType())
        .isEqualTo(KeyTemplate.OutputPrefixType.TINK);
    assertThat(KeyTemplates.get("JWT_PS256_3072_F4").getOutputPrefixType())
        .isEqualTo(KeyTemplate.OutputPrefixType.TINK);
    assertThat(KeyTemplates.get("JWT_PS384_3072_F4").getOutputPrefixType())
        .isEqualTo(KeyTemplate.OutputPrefixType.TINK);
    assertThat(KeyTemplates.get("JWT_PS512_4096_F4").getOutputPrefixType())
        .isEqualTo(KeyTemplate.OutputPrefixType.TINK);
  }

  @Test
  public void testRawTemplatesAreRaw() throws Exception {
    assertThat(KeyTemplates.get("JWT_PS256_2048_F4_RAW").getOutputPrefixType())
        .isEqualTo(KeyTemplate.OutputPrefixType.RAW);
    assertThat(KeyTemplates.get("JWT_PS256_3072_F4_RAW").getOutputPrefixType())
        .isEqualTo(KeyTemplate.OutputPrefixType.RAW);
    assertThat(KeyTemplates.get("JWT_PS384_3072_F4_RAW").getOutputPrefixType())
        .isEqualTo(KeyTemplate.OutputPrefixType.RAW);
    assertThat(KeyTemplates.get("JWT_PS512_4096_F4_RAW").getOutputPrefixType())
        .isEqualTo(KeyTemplate.OutputPrefixType.RAW);
  }

  @Test
  @Parameters(method = "templates")
  public void createSignVerify_success(String templateName) throws Exception {
    assumeFalse(TestUtil.isTsan());  // creating keys is too slow in Tsan.
    KeysetHandle handle = KeysetHandle.generateNew(KeyTemplates.get(templateName));
    JwtPublicKeySign signer = handle.getPrimitive(JwtPublicKeySign.class);
    JwtPublicKeyVerify verifier =
        handle.getPublicKeysetHandle().getPrimitive(JwtPublicKeyVerify.class);
    JwtValidator validator = JwtValidator.newBuilder().allowMissingExpiration().build();

    RawJwt rawToken = RawJwt.newBuilder().setJwtId("jwtId").withoutExpiration().build();
    String signedCompact = signer.signAndEncode(rawToken);
    VerifiedJwt verifiedToken = verifier.verifyAndDecode(signedCompact, validator);
    assertThat(verifiedToken.getJwtId()).isEqualTo("jwtId");
   assertThat(verifiedToken.hasTypeHeader()).isFalse();

    RawJwt rawTokenWithType =
        RawJwt.newBuilder().setTypeHeader("typeHeader").withoutExpiration().build();
    String signedCompactWithType = signer.signAndEncode(rawTokenWithType);
    VerifiedJwt verifiedTokenWithType =
        verifier.verifyAndDecode(
            signedCompactWithType,
            JwtValidator.newBuilder()
                .expectTypeHeader("typeHeader")
                .allowMissingExpiration()
                .build());
    assertThat(verifiedTokenWithType.getTypeHeader()).isEqualTo("typeHeader");
  }

  @Test
  @Parameters(method = "templates")
  public void createSignVerifyDifferentKey_throw(String templateName) throws Exception {
    assumeFalse(TestUtil.isTsan());  // creating keys is too slow in Tsan.
    KeyTemplate template = KeyTemplates.get(templateName);
    KeysetHandle handle = KeysetHandle.generateNew(template);
    JwtPublicKeySign signer = handle.getPrimitive(JwtPublicKeySign.class);
    RawJwt rawToken = RawJwt.newBuilder().setJwtId("id123").withoutExpiration().build();
    String signedCompact = signer.signAndEncode(rawToken);

    KeysetHandle otherHandle = KeysetHandle.generateNew(template);
    JwtPublicKeyVerify otherVerifier =
        otherHandle.getPublicKeysetHandle().getPrimitive(JwtPublicKeyVerify.class);
    JwtValidator validator = JwtValidator.newBuilder().allowMissingExpiration().build();
    assertThrows(
        GeneralSecurityException.class,
        () -> otherVerifier.verifyAndDecode(signedCompact, validator));
  }

  @Test
  @Parameters(method = "templates")
  public void createSignVerify_header_modification_throw(String templateName) throws Exception {
    assumeFalse(TestUtil.isTsan());  // creating keys is too slow in Tsan.
    KeysetHandle handle = KeysetHandle.generateNew(KeyTemplates.get(templateName));
    JwtPublicKeySign signer = handle.getPrimitive(JwtPublicKeySign.class);
    JwtPublicKeyVerify verifier =
        handle.getPublicKeysetHandle().getPrimitive(JwtPublicKeyVerify.class);
    RawJwt rawToken = RawJwt.newBuilder().setJwtId("id123").withoutExpiration().build();
    String signedCompact = signer.signAndEncode(rawToken);

    // Modify the header by adding a space at the end.
    String[] parts = signedCompact.split("\\.", -1);
    String header = new String(Base64.urlSafeDecode(parts[0]), UTF_8);
    String headerBase64 = Base64.urlSafeEncode((header + " ").getBytes(UTF_8));
    String modifiedCompact = headerBase64 + "." + parts[1] + "." + parts[2];

    JwtValidator validator = JwtValidator.newBuilder().allowMissingExpiration().build();
    assertThrows(
        GeneralSecurityException.class, () -> verifier.verifyAndDecode(modifiedCompact, validator));
  }

  @Test
  @Parameters(method = "templates")
  public void createSignVerify_payload_modification_throw(String templateName) throws Exception {
    assumeFalse(TestUtil.isTsan());  // creating keys is too slow in Tsan.
    KeysetHandle handle = KeysetHandle.generateNew(KeyTemplates.get(templateName));
    JwtPublicKeySign signer = handle.getPrimitive(JwtPublicKeySign.class);
    JwtPublicKeyVerify verifier =
        handle.getPublicKeysetHandle().getPrimitive(JwtPublicKeyVerify.class);
    RawJwt rawToken = RawJwt.newBuilder().setJwtId("id123").withoutExpiration().build();
    String signedCompact = signer.signAndEncode(rawToken);

    // Modify the payload by adding a space at the end.
    String[] parts = signedCompact.split("\\.", -1);
    String payload = new String(Base64.urlSafeDecode(parts[1]), UTF_8);
    String payloadBase64 = Base64.urlSafeEncode((payload + " ").getBytes(UTF_8));
    String modifiedCompact = parts[0] + "." + payloadBase64 + "." + parts[2];

    JwtValidator validator = JwtValidator.newBuilder().allowMissingExpiration().build();
    assertThrows(
        GeneralSecurityException.class, () -> verifier.verifyAndDecode(modifiedCompact, validator));
  }

  private static final RSAPrivateCrtKey createPrivateKey(JwtRsaSsaPssPrivateKey keyProto)
      throws GeneralSecurityException {
    KeyFactory kf = EngineFactory.KEY_FACTORY.getInstance("RSA");
    return (RSAPrivateCrtKey)
        kf.generatePrivate(
            new RSAPrivateCrtKeySpec(
                new BigInteger(1, keyProto.getPublicKey().getN().toByteArray()),
                new BigInteger(1, keyProto.getPublicKey().getE().toByteArray()),
                new BigInteger(1, keyProto.getD().toByteArray()),
                new BigInteger(1, keyProto.getP().toByteArray()),
                new BigInteger(1, keyProto.getQ().toByteArray()),
                new BigInteger(1, keyProto.getDp().toByteArray()),
                new BigInteger(1, keyProto.getDq().toByteArray()),
                new BigInteger(1, keyProto.getCrt().toByteArray())));
  }

  private static String generateSignedCompact(
      RsaSsaPssSignJce rawSigner, JsonObject header, JsonObject payload)
      throws GeneralSecurityException {
    String payloadBase64 = Base64.urlSafeEncode(payload.toString().getBytes(UTF_8));
    String headerBase64 = Base64.urlSafeEncode(header.toString().getBytes(UTF_8));
    String unsignedCompact = headerBase64 + "." + payloadBase64;
    String signature =
        Base64.urlSafeEncode(rawSigner.sign(unsignedCompact.getBytes(UTF_8)));
    return unsignedCompact + "." + signature;
  }

  @Test
  public void createSignVerify_withDifferentHeaders() throws Exception {
    assumeFalse(TestUtil.isTsan());  // creating keys is too slow in Tsan.
    KeyTemplate template = KeyTemplates.get("JWT_PS256_2048_F4");
    KeysetHandle handle = KeysetHandle.generateNew(template);
    Keyset keyset = CleartextKeysetHandle.getKeyset(handle);
    JwtRsaSsaPssPrivateKey keyProto =
        JwtRsaSsaPssPrivateKey.parseFrom(
            keyset.getKey(0).getKeyData().getValue(), ExtensionRegistryLite.getEmptyRegistry());
    RSAPrivateCrtKey privateKey = createPrivateKey(keyProto);

    JwtRsaSsaPssAlgorithm algorithm = keyProto.getPublicKey().getAlgorithm();
    Enums.HashType hash = JwtRsaSsaPssVerifyKeyManager.hashForPssAlgorithm(algorithm);
    int saltLength = JwtRsaSsaPssVerifyKeyManager.saltLengthForPssAlgorithm(algorithm);
    RsaSsaPssSignJce rawSigner = new RsaSsaPssSignJce(privateKey, hash, hash, saltLength);
    JwtPublicKeyVerify verifier =
        handle.getPublicKeysetHandle().getPrimitive(JwtPublicKeyVerify.class);
    JwtValidator validator = JwtValidator.newBuilder().allowMissingExpiration().build();

    JsonObject payload = new JsonObject();
    payload.addProperty(JwtNames.CLAIM_JWT_ID, "jwtId");

    // valid token, with "typ" set in the header
    JsonObject goodHeader = new JsonObject();
    goodHeader.addProperty(JwtNames.HEADER_ALGORITHM, "PS256");
    goodHeader.addProperty("typ", "typeHeader");
    String goodSignedCompact = generateSignedCompact(rawSigner, goodHeader, payload);
    verifier.verifyAndDecode(
        goodSignedCompact,
        JwtValidator.newBuilder().expectTypeHeader("typeHeader").allowMissingExpiration().build());

    // invalid token with an empty header
    JsonObject emptyHeader = new JsonObject();
    String emptyHeaderSignedCompact = generateSignedCompact(rawSigner, emptyHeader, payload);
    assertThrows(
        GeneralSecurityException.class,
        () -> verifier.verifyAndDecode(emptyHeaderSignedCompact, validator));

    // invalid token with a valid but incorrect algorithm in the header
    JsonObject badAlgoHeader = new JsonObject();
    badAlgoHeader.addProperty(JwtNames.HEADER_ALGORITHM, "RS256");
    String badAlgoSignedCompact = generateSignedCompact(rawSigner, badAlgoHeader, payload);
    assertThrows(
        GeneralSecurityException.class,
        () -> verifier.verifyAndDecode(badAlgoSignedCompact, validator));

    // token with an unknown "kid" in the header is valid
    JsonObject unknownKidHeader = new JsonObject();
    unknownKidHeader.addProperty(JwtNames.HEADER_ALGORITHM, "PS256");
    unknownKidHeader.addProperty("kid", "unknown");
    String unknownKidSignedCompact = generateSignedCompact(rawSigner, unknownKidHeader, payload);
    verifier.verifyAndDecode(unknownKidSignedCompact, validator);
  }

  /* Create a new keyset handle with the "custom_kid" value set. */
  private KeysetHandle withCustomKid(KeysetHandle keysetHandle, String customKid)
      throws Exception {
    Keyset keyset = CleartextKeysetHandle.getKeyset(keysetHandle);
    JwtRsaSsaPssPrivateKey privateKey =
        JwtRsaSsaPssPrivateKey.parseFrom(
            keyset.getKey(0).getKeyData().getValue(), ExtensionRegistryLite.getEmptyRegistry());
    JwtRsaSsaPssPublicKey publicKeyWithKid =
        privateKey.getPublicKey().toBuilder()
            .setCustomKid(CustomKid.newBuilder().setValue(customKid).build())
            .build();
    JwtRsaSsaPssPrivateKey privateKeyWithKid =
        privateKey.toBuilder().setPublicKey(publicKeyWithKid).build();
    KeyData keyDataWithKid =
        keyset.getKey(0).getKeyData().toBuilder()
            .setValue(privateKeyWithKid.toByteString())
            .build();
    Keyset.Key keyWithKid = keyset.getKey(0).toBuilder().setKeyData(keyDataWithKid).build();
    return CleartextKeysetHandle.fromKeyset(keyset.toBuilder().setKey(0, keyWithKid).build());
  }

  @Test
  public void signAndVerifyWithCustomKid() throws Exception {
    assumeFalse(TestUtil.isTsan()); // KeysetHandle.generateNew is too slow in Tsan.
    KeyTemplate template = KeyTemplates.get("JWT_PS256_2048_F4_RAW");
    KeysetHandle handleWithoutKid = KeysetHandle.generateNew(template);
    KeysetHandle handleWithKid =
        withCustomKid(handleWithoutKid, "Lorem ipsum dolor sit amet, consectetur adipiscing elit");

    JwtPublicKeySign signerWithKid = handleWithKid.getPrimitive(JwtPublicKeySign.class);

    RawJwt rawToken = RawJwt.newBuilder().setJwtId("jwtId").withoutExpiration().build();
    String signedCompactWithKid = signerWithKid.signAndEncode(rawToken);

    // Verify that the kid is set in the header
    String jsonHeader = JwtFormat.splitSignedCompact(signedCompactWithKid).header;
    String kid = JsonUtil.parseJson(jsonHeader).get("kid").getAsString();
    assertThat(kid).isEqualTo("Lorem ipsum dolor sit amet, consectetur adipiscing elit");

    JwtValidator validator = JwtValidator.newBuilder().allowMissingExpiration().build();
    JwtPublicKeyVerify verifier =
        handleWithoutKid.getPublicKeysetHandle().getPrimitive(JwtPublicKeyVerify.class);
    JwtPublicKeyVerify verifierWithKid =
        handleWithKid.getPublicKeysetHandle().getPrimitive(JwtPublicKeyVerify.class);

    // Both the verifiers accept signedCompactWithKid.
    assertThat(verifier.verifyAndDecode(signedCompactWithKid, validator).getJwtId())
        .isEqualTo("jwtId");
    assertThat(verifierWithKid.verifyAndDecode(signedCompactWithKid, validator).getJwtId())
        .isEqualTo("jwtId");

    JwtPublicKeySign signerWithoutKid = handleWithoutKid.getPrimitive(JwtPublicKeySign.class);
    String signedCompactWithoutKid = signerWithoutKid.signAndEncode(rawToken);

    // Both the verifiers accept signedCompactWithoutKid.
    assertThat(verifier.verifyAndDecode(signedCompactWithoutKid, validator).getJwtId())
        .isEqualTo("jwtId");
    assertThat(verifierWithKid.verifyAndDecode(signedCompactWithoutKid, validator).getJwtId())
        .isEqualTo("jwtId");
  }

  @Test
  public void signWithTinkKeyAndCustomKid_fails() throws Exception {
    assumeFalse(TestUtil.isTsan()); // KeysetHandle.generateNew is too slow in Tsan.
    KeyTemplate template = KeyTemplates.get("JWT_PS256_2048_F4");
    KeysetHandle handle = KeysetHandle.generateNew(template);

    // Create a new handle with the "kid" value set.
    Keyset keyset = CleartextKeysetHandle.getKeyset(handle);
    JwtRsaSsaPssPrivateKey privateKey =
        JwtRsaSsaPssPrivateKey.parseFrom(
            keyset.getKey(0).getKeyData().getValue(), ExtensionRegistryLite.getEmptyRegistry());
    JwtRsaSsaPssPublicKey publicKeyWithKid =
        privateKey.getPublicKey().toBuilder()
            .setCustomKid(
                CustomKid.newBuilder()
                    .setValue("Lorem ipsum dolor sit amet, consectetur adipiscing elit")
                    .build())
            .build();
    JwtRsaSsaPssPrivateKey privateKeyWithKid =
        privateKey.toBuilder().setPublicKey(publicKeyWithKid).build();
    KeyData keyDataWithKid =
        keyset.getKey(0).getKeyData().toBuilder()
            .setValue(privateKeyWithKid.toByteString())
            .build();
    Keyset.Key keyWithKid = keyset.getKey(0).toBuilder().setKeyData(keyDataWithKid).build();
    KeysetHandle handleWithKid =
        CleartextKeysetHandle.fromKeyset(keyset.toBuilder().setKey(0, keyWithKid).build());

    JwtPublicKeySign signerWithKid = handleWithKid.getPrimitive(JwtPublicKeySign.class);
    RawJwt rawToken = RawJwt.newBuilder().setJwtId("jwtId").withoutExpiration().build();
    assertThrows(JwtInvalidException.class, () -> signerWithKid.signAndEncode(rawToken));
  }
}
