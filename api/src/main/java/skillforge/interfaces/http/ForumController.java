package skillforge.interfaces.http;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import skillforge.application.catalog.ForumApplication;
import skillforge.domain.forum.Doubt;
import skillforge.domain.forum.Reply;

@RestController
class ForumController {

    private final ForumApplication forum;

    ForumController(ForumApplication forum) {
        this.forum = forum;
    }

    @GetMapping("/lessons/{id}/doubts")
    List<DoubtBody> doubts(Authentication authentication, @PathVariable UUID id) {
        return forum.list(RequestActor.from(authentication), id).stream().map(DoubtBody::from).toList();
    }

    @PostMapping("/lessons/{id}/doubts")
    ResponseEntity<IdName> open(Authentication authentication, @PathVariable UUID id, @RequestBody DoubtRequest body) {
        DoubtRequest request = body == null ? new DoubtRequest(null, null) : body;
        UUID created = forum.open(RequestActor.from(authentication), id, request.title(), request.body());
        return ResponseEntity.status(HttpStatus.CREATED).body(new IdName(created, request.title()));
    }

    @PostMapping("/lessons/{id}/doubts/{doubtId}/replies")
    ResponseEntity<Void> reply(
            Authentication authentication,
            @PathVariable UUID id,
            @PathVariable UUID doubtId,
            @RequestBody ReplyRequest body) {
        forum.reply(RequestActor.from(authentication), id, doubtId, body == null ? null : body.body());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    record DoubtRequest(String title, String body) {
    }

    record ReplyRequest(String body) {
    }

    record IdName(UUID id, String name) {
    }

    record ReplyBody(UUID id, String author, String body) {

        static ReplyBody from(Reply reply) {
            return new ReplyBody(reply.id(), reply.author(), reply.body());
        }

    }

    record DoubtBody(UUID id, String author, String title, String body, List<ReplyBody> replies) {

        static DoubtBody from(Doubt doubt) {
            return new DoubtBody(
                    doubt.id(),
                    doubt.author(),
                    doubt.title(),
                    doubt.body(),
                    doubt.replies().stream().map(ReplyBody::from).toList());
        }

    }

}
