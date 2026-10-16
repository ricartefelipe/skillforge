import Link from "next/link";

import {
  canLeadPractices,
  canManageCohorts,
  progressLabel,
  type BankItem,
  type CohortDetail,
  type PeerWork,
  type Practice,
  type RollCall,
  type ClassNotice,
  type StudyGroup,
} from "../../lib/cohort";
import { kindLabel } from "../../lib/catalog";
import { formatDeadline } from "../../lib/deadline";
import { LessonForm } from "../catalogo/lesson-form";
import { ExamForm } from "../exam-form";
import { GradeOrder } from "./grade-order";
import { ProgressMeter } from "../meter";

export function CohortDetailView({
  cohort,
  role,
  notice,
  enrollStudent,
  completeLesson,
  openPractice,
  submitPractice,
  returnPractice,
  openExam,
  takeExam,
  announcements = [],
  rolls = [],
  announce,
  callRoll,
  createModule,
  createLesson,
  removeLesson,
  removePractice,
  removeExam,
  withdrawNotice,
  bank = [],
  groups = [],
  peers = [],
  revisePractice,
  reviseAnnouncement,
  weighCohort,
  openGroup,
  reviewPeer,
  saveQuestion,
  reorderGrade,
  reviseQuestions,
}: {
  cohort: CohortDetail;
  role: string;
  notice: string | null;
  enrollStudent: (formData: FormData) => void;
  completeLesson: (formData: FormData) => void;
  openPractice: (formData: FormData) => void;
  submitPractice: (formData: FormData) => void;
  returnPractice: (formData: FormData) => void;
  openExam: (formData: FormData) => void;
  takeExam: (formData: FormData) => void;
  announcements?: ClassNotice[];
  rolls?: RollCall[];
  announce?: (formData: FormData) => void;
  callRoll?: (formData: FormData) => void;
  createModule?: (formData: FormData) => void;
  createLesson?: (formData: FormData) => void;
  removeLesson?: (formData: FormData) => void;
  removePractice?: (formData: FormData) => void;
  removeExam?: (formData: FormData) => void;
  withdrawNotice?: (formData: FormData) => void;
  bank?: BankItem[];
  groups?: StudyGroup[];
  peers?: PeerWork[];
  revisePractice?: (formData: FormData) => void;
  reviseAnnouncement?: (formData: FormData) => void;
  weighCohort?: (formData: FormData) => void;
  openGroup?: (formData: FormData) => void;
  reviewPeer?: (formData: FormData) => void;
  saveQuestion?: (formData: FormData) => void;
  reorderGrade?: (formData: FormData) => void;
  reviseQuestions?: (formData: FormData) => void;
}) {
  const leads = canLeadPractices(role);
  const here = `/turmas/${cohort.id}`;
  return (
    <div className="page">
      <header className="intro">
        <p className="kicker">Turma</p>
        <h1>{cohort.name}</h1>
        <p className="lede">
          {role === "aluno"
            ? "Conclua cada aula, entregue a prova nesta página e pegue o certificado no boletim. Ele sai com média 7 e, se houver chamada, 75% de presença."
            : "As aulas são o material do curso. Entrega, prova e chamada ficam nesta página."}
        </p>
      </header>
      {notice ? <p role="alert">{notice}</p> : null}
      <p className="meta">
        {cohort.course.name} · ministrada por {cohort.teacher}
      </p>
      <nav className="class-nav" aria-label="Seções da turma">
        <a href="#aulas">Aulas</a>
        <a href="#provas">Provas</a>
        <a href="#praticas">Práticas</a>
        <a href="#avisos">Avisos</a>
        <a href="#pessoas">Pessoas</a>
        <a href="#grupos">Grupos</a>
        <a href="#chamada">Chamada</a>
        {role === "aluno" ? <Link href="/boletim">Certificado</Link> : null}
      </nav>
      <section className="tree" id="aulas" aria-label="Aulas">
        <h2>Aulas</h2>
        {cohort.completed !== null && cohort.total !== null ? (
          <>
            <p className="tally">{progressLabel(cohort.completed, cohort.total)}</p>
            <ProgressMeter completed={cohort.completed} total={cohort.total} />
          </>
        ) : null}
        {cohort.course.modules.length === 0 ? <p className="empty">Nenhuma unidade ainda.</p> : null}
        {leads && reorderGrade ? (
          <GradeOrder
            courseId={cohort.course.id}
            returnTo={here}
            action={reorderGrade}
            modules={cohort.course.modules.map((module) => ({
              id: module.id,
              name: module.name,
              lessons: module.lessons.map((lesson) => ({ id: lesson.id, title: lesson.title })),
            }))}
          />
        ) : null}
        {leads && createModule ? (
          <form className="compose" action={createModule} aria-label="Nova unidade">
            <input type="hidden" name="courseId" value={cohort.course.id} />
            <input type="hidden" name="returnTo" value={here} />
            <label>
              Nome da unidade
              <input name="name" required />
            </label>
            <button type="submit">Abrir unidade</button>
          </form>
        ) : null}
        {cohort.course.modules.map((module, index) => (
          <section className="module unit" key={module.id} aria-label={module.name}>
            <p className="meta">Unidade {index + 1}</p>
            <h3>{module.name}</h3>
            <ul>
              {module.lessons.map((lesson) => (
                <li className="lesson-row" key={lesson.id}>
                  <Link href={`/catalogo/aulas/${lesson.id}`}>{lesson.title}</Link>
                  <span className="kind">{kindLabel(lesson.kind)}</span>
                  {lesson.completed === true ? <span className="done">Concluída</span> : null}
                  {leads && removeLesson ? (
                    <form action={removeLesson}>
                      <input type="hidden" name="lessonId" value={lesson.id} />
                      <input type="hidden" name="returnTo" value={here} />
                      <button className="quiet" type="submit">Retirar</button>
                    </form>
                  ) : null}
                  {role === "aluno" && lesson.completed === false ? (
                    <form action={completeLesson}>
                      <input type="hidden" name="cohortId" value={cohort.id} />
                      <input type="hidden" name="lessonId" value={lesson.id} />
                      <button className="quiet" type="submit">Concluir</button>
                    </form>
                  ) : null}
                </li>
              ))}
            </ul>
            {leads && createLesson ? (
              <LessonForm moduleId={module.id} action={createLesson} returnTo={here} />
            ) : null}
          </section>
        ))}
      </section>
      <section className="tree" id="atividades" aria-label="Atividades">
        <h2>Atividades</h2>
        <h3 id="provas">Provas</h3>
        {cohort.exams.length === 0 ? <p className="empty">Nenhuma prova ainda.</p> : null}
        {cohort.exams.map((exam) => {
          const own = role === "aluno" ? (exam.scores[0] ?? null) : null;
          return (
            <section className="module" key={exam.id} aria-label={exam.title}>
              <h3>{exam.title}</h3>
              {exam.dueAt ? <p className="meta">Prazo {formatDeadline(exam.dueAt)}</p> : null}
              <p className="meta">
                {exam.minutes ? `${exam.minutes} min · ` : null}
                {exam.attemptsAllowed === 1 ? "1 tentativa" : `${exam.attemptsAllowed} tentativas`}
              </p>
              {role === "aluno" && !lessonsReady(cohort) ? (
                <p className="meta">Conclua as aulas do curso para fazer esta prova.</p>
              ) : null}
              {role === "aluno" && lessonsReady(cohort) && exam.taken < exam.attemptsAllowed ? (
                <form className="compose" action={takeExam} aria-label={`Fazer ${exam.title}`}>
                  <input type="hidden" name="cohortId" value={cohort.id} />
                  <input type="hidden" name="examId" value={exam.id} />
                  {exam.minutes ? <input type="hidden" name="minutes" value={exam.minutes} /> : null}
                  {exam.questions.map((question) => (
                    <fieldset key={question.id}>
                      <legend>{question.prompt}</legend>
                      {question.choices.map((choice) => (
                        <label key={choice.id}>
                          <input type="radio" name={question.id} value={choice.id} required /> {choice.label}
                        </label>
                      ))}
                    </fieldset>
                  ))}
                  <button type="submit">Entregar prova</button>
                </form>
              ) : null}
              {role === "aluno" && own !== null ? <p className="tally">Nota {own.grade}</p> : null}
              {leads
                ? exam.scores.map((score) => (
                    <p key={score.student} className="tally">
                      {score.student} {score.grade}
                    </p>
                  ))
                : null}
              {leads && exam.scores.length === 0 ? <p className="meta">Ninguém fez esta prova.</p> : null}
              {leads && reviseQuestions ? (
                <form className="compose" action={reviseQuestions} aria-label={`Editar questões de ${exam.title}`}>
                  <input type="hidden" name="cohortId" value={cohort.id} />
                  <input type="hidden" name="examId" value={exam.id} />
                  {exam.questions.map((question) => (
                    <fieldset key={question.id}>
                      <label>
                        Enunciado
                        <textarea name={`prompt:${question.id}`} defaultValue={question.prompt} required />
                      </label>
                      {question.choices.map((choice) => (
                        <label key={choice.id}>
                          Alternativa
                          <input name={`choice:${question.id}/${choice.id}`} defaultValue={choice.label} required />
                        </label>
                      ))}
                      <label>
                        <input type="checkbox" name="retire" value={question.id} /> Retirar questão
                      </label>
                    </fieldset>
                  ))}
                  <label>
                    Nova questão
                    <input name="newPrompt" />
                  </label>
                  <label>
                    Alternativa correta
                    <input name="newRight" />
                  </label>
                  <label>
                    Alternativa errada
                    <input name="newWrong" />
                  </label>
                  <button type="submit">Guardar questões</button>
                </form>
              ) : null}
              {leads && removeExam ? (
                <form action={removeExam}>
                  <input type="hidden" name="cohortId" value={cohort.id} />
                  <input type="hidden" name="examId" value={exam.id} />
                  <button className="quiet" type="submit">Retirar prova</button>
                </form>
              ) : null}
            </section>
          );
        })}
        {leads ? <ExamForm cohortId={cohort.id} action={openExam} returnTo={here} bank={bank} /> : null}
        {leads && saveQuestion ? (
          <form className="compose" action={saveQuestion} aria-label="Nova questão no banco">
            <input type="hidden" name="cohortId" value={cohort.id} />
            <input type="hidden" name="courseId" value={cohort.course.id} />
            <label>
              Enunciado para o banco
              <textarea name="prompt" required />
            </label>
            <label>
              Alternativa correta
              <input name="right" required />
            </label>
            <label>
              Alternativa errada
              <input name="wrong" required />
            </label>
            <button type="submit">Guardar no banco</button>
          </form>
        ) : null}
        <h3 id="praticas">Práticas</h3>
        {cohort.practices.length === 0 ? <p className="empty">Nenhuma prática ainda.</p> : null}
        {cohort.practices.map((practice) => (
          <PracticeCard
            key={practice.id}
            cohortId={cohort.id}
            practice={practice}
            role={role}
            submitPractice={submitPractice}
            returnPractice={returnPractice}
            removePractice={removePractice}
            revisePractice={revisePractice}
            reviewPeer={reviewPeer}
            peers={peers.filter((work) => work.practiceId === practice.id)}
          />
        ))}
        {leads ? (
          <form className="compose" action={openPractice} aria-label="Nova prática">
            <input type="hidden" name="cohortId" value={cohort.id} />
            <label>
              Título
              <input name="title" required />
            </label>
            <label>
              Enunciado
              <textarea name="prompt" required />
            </label>
            <label>
              Prazo
              <input name="dueAt" type="datetime-local" />
            </label>
            <label>
              Critério da rubrica
              <input name="criterion" />
            </label>
            <label>
              Pontos do critério
              <input name="points" type="number" min={1} max={10} />
            </label>
            <button type="submit">Abrir prática</button>
          </form>
        ) : null}
      </section>
      <section className="tree" id="avisos" aria-label="Avisos">
        <h2>Avisos</h2>
        {announcements.length === 0 ? <p className="empty">Nenhum aviso.</p> : null}
        {announcements.map((item) => (
          <section key={item.id} className="module" aria-label={item.title}>
            <h3>{item.title}</h3>
            <p>{item.body}</p>
            {leads && reviseAnnouncement ? (
              <form className="compose" action={reviseAnnouncement} aria-label={`Editar ${item.title}`}>
                <input type="hidden" name="cohortId" value={cohort.id} />
                <input type="hidden" name="announcementId" value={item.id} />
                <label>
                  Título
                  <input name="title" defaultValue={item.title} required />
                </label>
                <label>
                  Aviso
                  <textarea name="body" defaultValue={item.body} required />
                </label>
                <button type="submit">Guardar aviso</button>
              </form>
            ) : null}
          </section>
        ))}
        {canLeadPractices(role) && announce ? (
          <form className="compose" action={announce} aria-label="Novo aviso">
            <input type="hidden" name="cohortId" value={cohort.id} />
            <label>
              Título
              <input name="title" required />
            </label>
            <label>
              Aviso
              <textarea name="body" required />
            </label>
            <button type="submit">Publicar aviso</button>
          </form>
        ) : null}
        {leads && withdrawNotice
          ? announcements.map((item) => (
              <form key={`retirar-${item.id}`} action={withdrawNotice}>
                <input type="hidden" name="cohortId" value={cohort.id} />
                <input type="hidden" name="announcementId" value={item.id} />
                <button className="quiet" type="submit">Retirar {item.title}</button>
              </form>
            ))
          : null}
      </section>
      <section className="tree" id="chamada" aria-label="Chamada">
        <h3>Chamada</h3>
        {rolls.length === 0 ? <p className="empty">Nenhuma chamada.</p> : null}
        <ul>
          {rolls.map((roll) => (
            <li key={roll.heldOn}>
              {roll.heldOn}
              {roll.marks.map((mark) => (
                <span key={mark.student} className="tally">
                  {mark.student} {mark.present ? "presente" : "ausente"}
                </span>
              ))}
            </li>
          ))}
        </ul>
        {canLeadPractices(role) && callRoll ? (
          <form className="compose" action={callRoll} aria-label="Nova chamada">
            <input type="hidden" name="cohortId" value={cohort.id} />
            <input type="hidden" name="roster" value={(cohort.students ?? []).map((student) => student.student).join(",")} />
            <label>
              Data
              <input name="heldOn" type="date" required />
            </label>
            {(cohort.students ?? []).map((student) => (
              <label key={student.student}>
                <input type="checkbox" name="present" value={student.student} /> {student.student}
              </label>
            ))}
            <button type="submit">Registrar chamada</button>
          </form>
        ) : null}
      </section>
      <section className="tree" id="pessoas" aria-label="Pessoas">
        <h2>Pessoas</h2>
        {cohort.students ? (
          <>
            <h3>Alunos</h3>
            {cohort.students.length === 0 ? (
              <p className="empty">Nenhum aluno matriculado.</p>
            ) : (
              <ul aria-label="Alunos">
                {cohort.students.map((student) => (
                  <li className="person" key={student.student}>
                    <span>{student.student}</span>
                    <span className="tally">{progressLabel(student.completed, student.total)}</span>
                    <ProgressMeter completed={student.completed} total={student.total} />
                  </li>
                ))}
              </ul>
            )}
          </>
        ) : null}
        {canManageCohorts(role) ? (
          <form className="compose" action={enrollStudent} aria-label="Matricular aluno">
            <input type="hidden" name="cohortId" value={cohort.id} />
            <label>
              Conta do aluno
              <input name="student" required autoComplete="off" />
            </label>
            <button type="submit">Matricular</button>
          </form>
        ) : null}
      </section>
      <section className="tree" id="grupos" aria-label="Grupos">
        <h2>Grupos</h2>
        {groups.length === 0 ? <p className="empty">Nenhum grupo.</p> : null}
        {groups.map((group) => (
          <section key={group.id} className="module" aria-label={group.name}>
            <h3>{group.name}</h3>
            <p>{group.members.length === 0 ? "Sem alunos." : group.members.join(", ")}</p>
          </section>
        ))}
        {leads && openGroup ? (
          <form className="compose" action={openGroup} aria-label="Novo grupo">
            <input type="hidden" name="cohortId" value={cohort.id} />
            <label>
              Nome do grupo
              <input name="name" required />
            </label>
            <label>
              Contas, separadas por vírgula
              <input name="members" />
            </label>
            <button type="submit">Abrir grupo</button>
          </form>
        ) : null}
        {leads && weighCohort ? (
          <form className="compose" action={weighCohort} aria-label="Pesos da média">
            <input type="hidden" name="cohortId" value={cohort.id} />
            <label>
              Peso das práticas
              <input name="practiceWeight" type="number" min={1} max={5} defaultValue={1} required />
            </label>
            <label>
              Peso das provas
              <input name="examWeight" type="number" min={1} max={5} defaultValue={1} required />
            </label>
            <button type="submit">Guardar pesos</button>
          </form>
        ) : null}
      </section>
    </div>
  );
}

function lessonsReady(cohort: CohortDetail): boolean {
  if (cohort.total == null || cohort.total === 0) {
    return true;
  }
  return (cohort.completed ?? 0) >= cohort.total;
}

function PracticeCard({
  cohortId,
  practice,
  role,
  submitPractice,
  returnPractice,
  removePractice,
  revisePractice,
  reviewPeer,
  peers,
}: {
  cohortId: string;
  practice: Practice;
  role: string;
  submitPractice: (formData: FormData) => void;
  returnPractice: (formData: FormData) => void;
  removePractice?: (formData: FormData) => void;
  revisePractice?: (formData: FormData) => void;
  reviewPeer?: (formData: FormData) => void;
  peers: PeerWork[];
}) {
  const own = role === "aluno" ? (practice.deliveries[0] ?? null) : null;
  const waiting = own !== null && own.feedback === null;
  return (
    <section className="module" aria-label={practice.title}>
      <h3>{practice.title}</h3>
      {canLeadPractices(role) && removePractice ? (
        <form action={removePractice}>
          <input type="hidden" name="cohortId" value={cohortId} />
          <input type="hidden" name="practiceId" value={practice.id} />
          <button className="quiet" type="submit">Retirar prática</button>
        </form>
      ) : null}
      <p>{practice.prompt}</p>
      {practice.dueAt ? <p className="meta">Prazo {formatDeadline(practice.dueAt)}</p> : null}
      {practice.criteria.length > 0 ? (
        <ul>
          {practice.criteria.map((criterion) => (
            <li key={criterion.id}>
              {criterion.name} <span className="tally">{criterion.points}</span>
            </li>
          ))}
        </ul>
      ) : null}
      {role === "aluno" && own === null ? (
        <form className="compose" action={submitPractice} aria-label="Entrega">
          <input type="hidden" name="cohortId" value={cohortId} />
          <input type="hidden" name="practiceId" value={practice.id} />
          <label>
            Texto
            <textarea name="text" required />
          </label>
          <button type="submit">Entregar</button>
        </form>
      ) : null}
      {role === "aluno" && own !== null ? (
        <>
          <p>{own.text}</p>
          {own.feedback === null ? <p className="meta">Aguardando devolutiva.</p> : <p>{own.feedback}</p>}
          {own.grade !== null ? <p className="tally">Nota {own.grade}</p> : null}
          {waiting ? (
            <form className="compose" action={submitPractice} aria-label="Entrega">
              <input type="hidden" name="cohortId" value={cohortId} />
              <input type="hidden" name="practiceId" value={practice.id} />
              <label>
                Texto
                <textarea name="text" required defaultValue={own.text} />
              </label>
              <button className="quiet" type="submit">Entregar de novo</button>
            </form>
          ) : null}
        </>
      ) : null}
      {canLeadPractices(role)
        ? practice.deliveries.map((delivery) => (
            <section key={delivery.student} aria-label={delivery.student}>
              <p className="meta">{delivery.student}</p>
              <p>{delivery.text}</p>
              {delivery.feedback === null ? (
                <form className="compose" action={returnPractice} aria-label={`Devolutiva para ${delivery.student}`}>
                  <input type="hidden" name="cohortId" value={cohortId} />
                  <input type="hidden" name="practiceId" value={practice.id} />
                  <input type="hidden" name="student" value={delivery.student} />
                  <label>
                    Devolutiva
                    <textarea name="text" required />
                  </label>
                  {practice.criteria.length === 0 ? (
                    <label>
                      Nota
                      <select name="grade" required defaultValue="10">
                        {Array.from({ length: 11 }, (_, mark) => (
                          <option key={mark} value={mark}>
                            {mark}
                          </option>
                        ))}
                      </select>
                    </label>
                  ) : (
                    practice.criteria.map((criterion) => (
                      <label key={criterion.id}>
                        {criterion.name}
                        <input name={`mark:${criterion.id}`} type="number" min={0} max={criterion.points} required />
                      </label>
                    ))
                  )}
                  <button type="submit">Devolver</button>
                </form>
              ) : (
                <p>{delivery.feedback}</p>
              )}
            </section>
          ))
        : null}
      {canLeadPractices(role) && revisePractice && practice.deliveries.length === 0 ? (
        <form className="compose" action={revisePractice} aria-label={`Editar ${practice.title}`}>
          <input type="hidden" name="cohortId" value={cohortId} />
          <input type="hidden" name="practiceId" value={practice.id} />
          <label>
            Título
            <input name="title" defaultValue={practice.title} required />
          </label>
          <label>
            Enunciado
            <textarea name="prompt" defaultValue={practice.prompt} required />
          </label>
          <label>
            Prazo
            <input name="dueAt" type="datetime-local" />
          </label>
          <button type="submit">Guardar prática</button>
        </form>
      ) : null}
      {peers.map((work) => (
        <section key={work.author} aria-label={`Entrega de ${work.author}`}>
          <p className="meta">{work.author}</p>
          <p>{work.text}</p>
          {work.reviews.map((review) => (
            <p key={review.reviewer}>
              {review.reviewer}: {review.comment} ({review.mark})
            </p>
          ))}
          {role === "aluno" && reviewPeer && work.reviews.length === 0 ? (
            <form className="compose" action={reviewPeer} aria-label={`Avaliar ${work.author}`}>
              <input type="hidden" name="cohortId" value={cohortId} />
              <input type="hidden" name="practiceId" value={practice.id} />
              <input type="hidden" name="author" value={work.author} />
              <label>
                Comentário
                <textarea name="comment" required />
              </label>
              <label>
                Nota
                <input name="mark" type="number" min={0} max={10} required />
              </label>
              <button type="submit">Avaliar colega</button>
            </form>
          ) : null}
        </section>
      ))}
    </section>
  );
}
