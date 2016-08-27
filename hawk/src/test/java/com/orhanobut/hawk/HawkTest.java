package com.orhanobut.hawk;

import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static com.google.common.truth.Truth.assertThat;
import static junit.framework.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 21)
public class HawkTest {

  final String key = "key";
  final String value = "foo";
  final String cipherText = "123345";
  final String withType = "java.lang.String##0V@123345";

  Context context;

  @Mock Converter converter;
  @Mock Storage storage;
  @Mock Encryption encryption;

  @Before public void setUp() {
    context = RuntimeEnvironment.application;

    initMocks(this);

    Hawk.build(
        new HawkBuilder(context)
            .setConverter(converter)
            .setStorage(storage)
            .setEncryption(encryption)
    );
  }

  @After public void tearDown() {
    Hawk.destroy();
  }

  //region INIT
  @Test public void initRequiresContext() {
    try {
      Hawk.init(null);
      fail("context should not be null");
    } catch (Exception e) {
      assertThat(e).hasMessage("Context should not be null");
    }
  }

  @Test public void returnHawkBuilderOnInitAndDestroyHawkInternal() {
    HawkBuilder builder = Hawk.init(context);

    assertThat(builder).isNotNull();
    assertThat(Hawk.HAWK).isNull();
  }

  @Test public void testIsBuilt() {
    assertThat(Hawk.isBuilt()).isTrue();
  }
  //endregion

  //region OTHERS
  @Test public void testDestroy() {
    Hawk.destroy();

    assertThat(Hawk.HAWK).isNull();
  }

  @Test public void testResetCrypto() {
    when(encryption.reset()).thenReturn(true);

    assertThat(Hawk.resetCrypto()).isTrue();

    verify(encryption).reset();
    verifyZeroInteractions(storage, converter);
  }
  //endregion

  //region PUT
  @Test public void testPut() throws Exception {
    when(converter.toString(value)).thenReturn(value);
    when(encryption.encrypt("key", value)).thenReturn(cipherText);
    when(storage.put(key, withType)).thenReturn(true);

    assertThat(Hawk.put(key, value)).isTrue();

    InOrder inOrder = inOrder(storage, converter, encryption);
    inOrder.verify(converter).toString(value);
    inOrder.verify(encryption).encrypt("key", value);
    inOrder.verify(storage).put(key, withType);
  }

  @Test public void testNullKeyOnPut() {
    try {
      Hawk.put(null, "foo");
      fail("Key should not be null");
    } catch (Exception e) {
      assertThat(e).hasMessage("Key should not be null");
    }
  }

  @Test public void validateBuildOnPut() {
    try {
      Hawk.destroy();
      Hawk.init(context);
      Hawk.put(key, value);
      fail("build is not complete");
    } catch (Exception e) {
    }
  }

  @Test public void removeWhenValueIsNullOnPut() {
    when(storage.delete(key)).thenReturn(true);

    assertThat(Hawk.put(key, null)).isTrue();

    verify(storage).delete(key);
  }

  @Test public void returnFalseAndNotAddToStorageWhenEncryptionFailsOnPut() throws Exception {
    when(converter.toString(value)).thenReturn(value);
    when(encryption.encrypt("key", value)).thenReturn(null);

    assertThat(Hawk.put(key, value)).isFalse();

    InOrder inOrder = inOrder(storage, converter, encryption);
    inOrder.verify(converter).toString(value);
    inOrder.verify(encryption).encrypt("key", value);
    verifyZeroInteractions(storage);
  }

  @Test public void returnFalseAndNotAddToStorageWhenEncodingFailsOnPut() {
    when(converter.toString(value)).thenReturn(null);

    assertThat(Hawk.put(key, value)).isFalse();

    verify(converter).toString(value);
    verifyZeroInteractions(storage, encryption);
  }

  //endregion

  //region GET

  @Test public void returnValueOnGet() throws Exception {
    when(storage.get(key)).thenReturn(withType);
    when(encryption.decrypt("key", cipherText)).thenReturn(value);

    Hawk.get(key);

    InOrder inOrder = inOrder(storage, converter, encryption);
    inOrder.verify(storage).get(key);
    inOrder.verify(encryption).decrypt("key", cipherText);
    inOrder.verify(converter).fromString(eq(value), any(DataInfo.class));
  }

  @Test public void returnDefaultValueOnGetWithDefault() throws Exception {
    assertThat(Hawk.get(key, "default")).isEqualTo("default");

    verify(storage).get(key);
    verifyZeroInteractions(converter, encryption);
  }

  @Test public void returnValueOnGetWithDefault() throws Exception {
    when(storage.get(key)).thenReturn(withType);
    when(encryption.decrypt("key", cipherText)).thenReturn(value);
    when(converter.fromString(eq(value), any(DataInfo.class))).thenReturn(value);

    assertThat(Hawk.get(key, "default")).isEqualTo(value);

    verify(storage).get(key);
    verify(converter).fromString(any(String.class), any(DataInfo.class));
    verify(encryption).decrypt("key", cipherText);
  }

  @Test public void keyShouldBeValidOnGet() {
    try {
      Hawk.get(null);
      fail("Key should not be null");
    } catch (Exception e) {
      assertThat(e).hasMessage("Key should not be null");
    }
  }

  @Test public void validateBuildOnGet() {
    try {
      Hawk.destroy();

      Hawk.init(context);
      Hawk.get(key);
      fail("should throw exception");
    } catch (Exception e) {
    }
  }

  @Test public void returnNullIfKeyIsNotInStorageOnGet() {
    when(storage.get(key)).thenReturn(null);

    assertThat(Hawk.get(key)).isNull();

    verify(storage).get(key);
    verifyZeroInteractions(converter, encryption);
  }

  @Test public void throwExceptionIfDataIsCorruptedOnGet() {
    when(storage.get(key)).thenReturn("234234");

    try {
      Hawk.get(key);
      fail("Text should contain delimiter");
    } catch (Exception e) {
    }

    verify(storage).get(key);
    verifyZeroInteractions(converter, encryption);
  }

  @Test public void returnNullIfEncryptionFailsOnGet() throws Exception {
    when(storage.get(key)).thenReturn(withType);

    assertThat(Hawk.get(key)).isNull();

    verify(storage).get(key);
    verify(encryption).decrypt("key", cipherText);

    verifyZeroInteractions(converter);
  }

  @Test public void returnNullIfEncodingFailsOnGet() throws Exception {
    when(storage.get(key)).thenReturn(withType);
    when(encryption.decrypt("key", cipherText)).thenReturn(value);

    assertThat(Hawk.get(key)).isNull();

    verify(storage).get(key);
    verify(encryption).decrypt("key", cipherText);
    verify(converter).fromString(eq(value), any(DataInfo.class));
  }

  //endregion

  //region REMOVE
  @Test public void testRemove() {
    when(storage.delete(key)).thenReturn(true);

    assertThat(Hawk.delete(key)).isTrue();

    verify(storage).delete(key);
    verifyZeroInteractions(converter, encryption);
  }

  @Test public void testRemoveMultiple() {
    //TODO
  }

  @Test public void validateBuildOnRemove() {
    try {
      Hawk.destroy();
      Hawk.init(context);
      Hawk.delete(key);
      fail("");
    } catch (Exception e) {
    }
  }
  //endregion

  //region COUNT
  @Test public void testCount() {
    when(storage.count()).thenReturn(10L);

    assertThat(Hawk.count()).isEqualTo(10);

    verify(storage).count();
    verifyZeroInteractions(converter, encryption);
  }

  @Test public void validateBuildOnCount() {
    try {
      Hawk.destroy();
      Hawk.init(context);
      Hawk.count();
      fail("");
    } catch (Exception e) {
    }
  }
  //endregion

  //region CLEAR
  @Test public void testClear() {
    when(storage.clear()).thenReturn(true);

    assertThat(Hawk.clear()).isTrue();

    verify(storage).clear();
    verifyZeroInteractions(converter, encryption);
  }

  @Test public void validateBuildOnClear() {
    try {
      Hawk.destroy();
      Hawk.init(context);
      Hawk.clear();
      fail("");
    } catch (Exception e) {
    }
  }
  //endregion

  //region CONTAINS
  @Test public void testContains() {
    when(storage.contains(key)).thenReturn(true);

    assertThat(Hawk.contains(key)).isTrue();

    verify(storage).contains(key);
    verifyZeroInteractions(converter, encryption);
  }

  @Test public void validateBuildOnContains() {
    try {
      Hawk.destroy();
      Hawk.init(context);
      Hawk.contains(key);
      fail("");
    } catch (Exception e) {
    }
  }
  //endregion

}
