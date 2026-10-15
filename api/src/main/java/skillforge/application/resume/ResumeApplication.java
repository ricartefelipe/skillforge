package skillforge.application.resume;

import java.util.Optional;

import skillforge.domain.identity.Actor;
import skillforge.domain.resume.ResumeRepository;
import skillforge.domain.studio.Resume;

public class ResumeApplication {

    private final ResumeRepository resumes;

    public ResumeApplication(ResumeRepository resumes) {
        this.resumes = resumes;
    }

    public Optional<Resume> resume(Actor actor) {
        return resumes.resume(actor);
    }

}
