package skillforge.domain.resume;

import java.util.Optional;

import skillforge.domain.identity.Actor;
import skillforge.domain.studio.Resume;

public interface ResumeRepository {

    Optional<Resume> resume(Actor actor);

}
