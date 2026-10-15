package skillforge.domain.search;

import java.util.List;

import skillforge.domain.identity.Actor;
import skillforge.domain.studio.Hit;

public interface SearchRepository {

    List<Hit> search(Actor actor, String query);

}
