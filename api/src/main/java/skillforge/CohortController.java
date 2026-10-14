package skillforge;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CohortController {

    private final CohortStore store;

    CohortController(CohortStore store) {
        this.store = store;
    }

    @GetMapping("/cohorts")
    List<Map<String, Object>> cohorts(Authentication authentication) {
        Caller caller = caller(authentication);
        return store.visible(caller.role(), caller.username()).stream()
                .map(CohortController::summary)
                .toList();
    }

    @GetMapping("/cohorts/{id}")
    Map<String, Object> cohort(Authentication authentication, @PathVariable UUID id) {
        Caller caller = caller(authentication);
        CohortStore.CohortPage page = store.page(id, caller.role(), caller.username())
                .orElseThrow(() -> new CatalogRejection("not-found", HttpStatus.NOT_FOUND));
        Map<String, Object> body = summary(page.cohort());
        body.put("course", CatalogController.CourseBody.from(page.course()));
        if (page.students() != null) {
            body.put("students", page.students());
        }
        return body;
    }

    @PostMapping("/cohorts")
    ResponseEntity<CatalogController.IdName> open(Authentication authentication, @RequestBody OpenBody body) {
        requireAdministration(authentication);
        OpenBody request = body == null ? new OpenBody(null, null, null) : body;
        String name = Names.required(request.name(), "blank-cohort");
        String teacher = Names.required(request.teacher(), "blank-teacher");
        if (request.courseId() == null) {
            throw new CatalogRejection("not-found", HttpStatus.NOT_FOUND);
        }
        UUID id = store.open(request.courseId(), name, teacher);
        return ResponseEntity.status(HttpStatus.CREATED).body(new CatalogController.IdName(id, name));
    }

    @PostMapping("/cohorts/{id}/enrollments")
    ResponseEntity<Void> enroll(Authentication authentication, @PathVariable UUID id, @RequestBody EnrollBody body) {
        requireAdministration(authentication);
        String student = Names.required(body == null ? null : body.student(), "blank-student");
        store.enroll(id, student);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    private static Map<String, Object> summary(CohortStore.CohortRow row) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", row.id());
        body.put("name", row.name());
        body.put("courseId", row.courseId());
        body.put("courseName", row.courseName());
        body.put("teacher", row.teacher());
        if (row.students() != null) {
            body.put("students", row.students());
        }
        return body;
    }

    private static void requireAdministration(Authentication authentication) {
        if (!"administracao".equals(caller(authentication).role())) {
            throw new CatalogRejection("forbidden", HttpStatus.FORBIDDEN);
        }
    }

    private static Caller caller(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new CatalogRejection("forbidden", HttpStatus.FORBIDDEN);
        }
        Optional<String> role = InstitutionalRole.only(InstitutionalRole.fromToken(jwt));
        if (role.isEmpty()) {
            throw new CatalogRejection("forbidden", HttpStatus.FORBIDDEN);
        }
        String username = jwt.getClaimAsString("preferred_username");
        if (username == null) {
            username = "";
        }
        return new Caller(role.get(), username.trim());
    }

    record OpenBody(String name, UUID courseId, String teacher) {
    }

    record EnrollBody(String student) {
    }

    private record Caller(String role, String username) {
    }

}
