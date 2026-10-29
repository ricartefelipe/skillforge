package skillforge.domain.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseRepository {

    UUID addCourse(String name);

    UUID addModule(UUID courseId, String name);

    UUID addLesson(UUID moduleId, String title, LessonContent.Accepted content, MediaUpload media);

    UUID addScorm(UUID moduleId, String title, MediaUpload zip);

    void saveTool(UUID lessonId, String url, String secret);

    ScormOutline scormOutline(UUID lessonId, String student);

    Optional<MediaStream> scormFile(UUID lessonId, String path);

    /** Emite um bilhete de leitura dos arquivos da lição para quem já abriu a aula. */
    UUID issueScormTicket(UUID lessonId, String username);

    /** Quem o bilhete autoriza a ler os arquivos desta lição, se ele existir e ainda valer. */
    Optional<String> scormTicketHolder(UUID ticket, UUID lessonId);

    void trackScorm(UUID lessonId, String student, String status, Integer score, int position);

    void modernTool(UUID lessonId, String authLogin);

    java.util.Optional<String> toolAuth(UUID lessonId);

    void rememberHint(UUID token, String username, UUID lessonId, String nonce);

    java.util.Optional<String> takeHint(UUID token);

    String platformKid();

    String platformPrivate();

    String platformPublic();

    Optional<String> toolUrl(UUID lessonId);

    Optional<String> toolSecret(UUID lessonId);

    void reorder(UUID courseId, java.util.List<ModuleOrder> modules);

    void removeLesson(UUID lessonId);

    void reviseLesson(UUID lessonId, String title, String markdown);

    UUID addAsset(UUID lessonId, String name, MediaUpload media, String mediaType);

    List<LessonAsset> assets(UUID lessonId);

    Optional<MediaStream> asset(UUID lessonId, UUID assetId);

    void removeAsset(UUID lessonId, UUID assetId);

    Optional<UUID> courseOfLesson(UUID lessonId);

    List<Course> list();

    Optional<Course> find(UUID id);

    Optional<LessonText> lesson(UUID id);

    Optional<MediaStream> media(UUID id);

    Optional<UUID> courseOfModule(UUID moduleId);

}
