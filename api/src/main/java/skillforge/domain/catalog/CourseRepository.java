package skillforge.domain.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseRepository {

    UUID addCourse(String name);

    UUID addModule(UUID courseId, String name);

    UUID addLesson(UUID moduleId, String title, LessonContent.Accepted content);

    void removeLesson(UUID lessonId);

    void reviseLesson(UUID lessonId, String title, String markdown);

    UUID addAsset(UUID lessonId, String name, byte[] media, String mediaType);

    List<LessonAsset> assets(UUID lessonId);

    Optional<LessonFile> asset(UUID lessonId, UUID assetId);

    void removeAsset(UUID lessonId, UUID assetId);

    Optional<UUID> courseOfLesson(UUID lessonId);

    List<Course> list();

    Optional<Course> find(UUID id);

    Optional<LessonText> lesson(UUID id);

    Optional<LessonFile> media(UUID id);

    Optional<UUID> courseOfModule(UUID moduleId);

}
