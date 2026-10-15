"use client";

import { useState } from "react";

type ModuleOrder = { id: string; name: string; lessons: { id: string; title: string }[] };

export function GradeOrder({
  courseId,
  returnTo,
  modules,
  action,
}: {
  courseId: string;
  returnTo: string;
  modules: ModuleOrder[];
  action: (formData: FormData) => void;
}) {
  const [order, setOrder] = useState(modules);
  const [dragged, setDragged] = useState<string | null>(null);

  return (
    <section aria-label="Ordem da grade">
      <h3>Arraste as aulas</h3>
      {order.map((module) => (
        <div
          key={module.id}
          onDragOver={(event) => event.preventDefault()}
          onDrop={() => drop(module.id)}
        >
          <p className="meta">{module.name}</p>
          <ul>
            {module.lessons.map((lesson) => (
              <li key={lesson.id} draggable onDragStart={() => setDragged(lesson.id)}>
                {lesson.title}
              </li>
            ))}
          </ul>
        </div>
      ))}
    </section>
  );

  function drop(moduleId: string) {
    if (dragged === null) {
      return;
    }
    const next = order.map((module) => ({
      ...module,
      lessons: module.lessons.filter((lesson) => lesson.id !== dragged),
    }));
    const lesson = order.flatMap((module) => module.lessons).find((item) => item.id === dragged);
    if (!lesson) {
      return;
    }
    const placed = next.map((module) =>
      module.id === moduleId ? { ...module, lessons: [...module.lessons, lesson] } : module,
    );
    setOrder(placed);
    setDragged(null);
    const data = new FormData();
    data.set("courseId", courseId);
    data.set("returnTo", returnTo);
    data.set(
      "order",
      JSON.stringify({
        modules: placed.map((module) => ({ id: module.id, lessons: module.lessons.map((item) => item.id) })),
      }),
    );
    action(data);
  }
}
