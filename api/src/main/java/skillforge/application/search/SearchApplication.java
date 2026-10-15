package skillforge.application.search;

import java.util.List;

import skillforge.domain.identity.Actor;
import skillforge.domain.search.SearchRepository;
import skillforge.domain.shared.Failure;
import skillforge.domain.shared.Rejection;
import skillforge.domain.studio.Hit;

public class SearchApplication {

    private final SearchRepository search;

    public SearchApplication(SearchRepository search) {
        this.search = search;
    }

    public List<Hit> search(Actor actor, String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.length() < 2) {
            return List.of();
        }
        if (query.length() > 80) {
            throw new Rejection(Failure.TOO_LARGE);
        }
        return search.search(actor, query);
    }

}
