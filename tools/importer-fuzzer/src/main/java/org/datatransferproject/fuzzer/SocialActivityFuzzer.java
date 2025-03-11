package org.datatransferproject.fuzzer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.datatransferproject.types.common.models.social.SocialActivityActor;
import org.datatransferproject.types.common.models.social.SocialActivityAttachment;
import org.datatransferproject.types.common.models.social.SocialActivityAttachmentType;
import org.datatransferproject.types.common.models.social.SocialActivityContainerResource;
import org.datatransferproject.types.common.models.social.SocialActivityLocation;
import org.datatransferproject.types.common.models.social.SocialActivityModel;
import org.datatransferproject.types.common.models.social.SocialActivityType;

public class SocialActivityFuzzer extends BaseFuzzer<SocialActivityContainerResource> {
  SocialActivityFuzzer(Random random) {
    super(random);
  }

  private List<SocialActivityAttachment> nullableAttachments() {
    if (random.nextBoolean()) {
      return null;
    }
    var attachments = new ArrayList<SocialActivityAttachment>();
    var count = random.nextInt(4);
    for (int i = 0; i < count; i++) {
      attachments.add(
          new SocialActivityAttachment(
              nullableEnum(SocialActivityAttachmentType.class),
              nullableURL().toString(),
              generateID().toString(),
              generateID().toString()));
    }
    return attachments;
  }

  private SocialActivityLocation nullableLocation() {
    if (random.nextBoolean()) {
      return null;
    }
    var name = random.nextBoolean() ? "Null Island" : null;
    double longitude = (random.nextDouble() - 0.5) * 180;
    double latitude = (random.nextDouble() - 0.5) * 360;
    return new SocialActivityLocation(name, longitude, latitude);
  }

  private String nullableActivityTitle() {
    if (random.nextBoolean()) {
      return null;
    }

    return "Activity " + generateID();
  }

  private String nullableActivityContent() {
    if (random.nextBoolean()) {
      return null;
    }

    return "Content " + generateID();
  }

  private SocialActivityModel generateActivity() {
    return new SocialActivityModel(
        generateID().toString(),
        nullableInstant(),
        nullableEnum(SocialActivityType.class),
        nullableAttachments(),
        nullableLocation(),
        nullableActivityTitle(),
        nullableActivityContent(),
        nullableURL().toString());
  }

  public SocialActivityContainerResource generateContainer() {
    var actor =
        random.nextBoolean()
            ? new SocialActivityActor(
                generateID().toString(), nullableName(), nullableURL().toString())
            : null;
    var activities = new ArrayList<SocialActivityModel>();
    var count = random.nextInt(9) + 1;
    for (int i = 0; i < count; i++) {
      activities.add(generateActivity());
    }
    return new SocialActivityContainerResource(generateID().toString(), actor, activities);
  }
}
