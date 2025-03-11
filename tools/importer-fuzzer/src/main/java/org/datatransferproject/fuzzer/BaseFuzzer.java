package org.datatransferproject.fuzzer;

import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.Random;
import java.util.UUID;
import org.datatransferproject.types.common.models.ContainerResource;

public abstract class BaseFuzzer<T extends ContainerResource> {

  static final long JAN_1ST_2015 = 1420070400;

  Random random;

  BaseFuzzer(Random random) {
    this.random = random;
  }

  UUID generateID() {
    var buf = new byte[4];
    random.nextBytes(buf);
    return UUID.nameUUIDFromBytes(buf);
  }

  UUID nullableID() {
    return random.nextBoolean() ? generateID() : null;
  }

  String nullableName() {
    return random.nextBoolean() ? "Jane Doe" : null;
  }

  URL nullableURL() {
    try {
      return random.nextBoolean() ? new URL("http://example.com") : null;
    } catch (MalformedURLException e) {
      throw new UncheckedIOException(e);
    }
  }

  <E extends Enum<E>> E nullableEnum(Class<E> enumClass) {
    if (random.nextBoolean()) {
      return null;
    }
    var options = enumClass.getEnumConstants();
    return options[random.nextInt(options.length)];
  }

  Instant nullableInstant() {
    if (random.nextBoolean()) {
      return null;
    }
    var epochSecond = random.nextInt(60 * 60 * 24 * 365 * 10 /* 10 years */) + JAN_1ST_2015;
    return Instant.ofEpochSecond(epochSecond);
  }

  public abstract T generateContainer();
}
