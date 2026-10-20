package br.com.critplay.social;

import br.com.critplay.shared.BadRequestException;
import br.com.critplay.shared.NotFoundException;
import br.com.critplay.users.AppUser;
import br.com.critplay.users.UserService;
import org.springframework.stereotype.Service;

/** Profile discovery, privacy and locked interaction rules, independent of response rendering. */
@Service
public class SocialAccessService {
    private final UserService users;
    private final SocialGraphRepository graph;
    private final BlockService blocks;

    public SocialAccessService(UserService users, SocialGraphRepository graph, BlockService blocks) {
        this.users = users;
        this.graph = graph;
        this.blocks = blocks;
    }

    public record ProfileAccess(AppUser target, AppUser actor, boolean own, boolean visible) {
        public long viewerId() {
            return actor == null ? -1 : actor.getId();
        }
    }

    public record Interaction(long actorId, long targetId) {}

    public ProfileAccess profile(String viewer, String targetName) {
        var target = users.require(targetName);
        var actor = viewer == null ? null : users.require(viewer);
        return profile(actor, target);
    }

    public ProfileAccess profile(AppUser actor, AppUser target) {
        if (!target.isEnabled() || actor != null && blocks.blocked(actor.getId(), target.getId())) {
            throw new NotFoundException("Perfil não encontrado.");
        }
        boolean own = actor != null && actor.getId().equals(target.getId());
        boolean friend = actor != null && graph.areFriends(actor.getId(), target.getId());
        if (!own && !target.isDiscoverable() && !friend) {
            throw new NotFoundException("Perfil não encontrado.");
        }
        boolean visible = own || target.getVisibility() == ProfileVisibility.PUBLIC
            || target.getVisibility() == ProfileVisibility.FRIENDS && friend;
        return new ProfileAccess(target, actor, own, visible);
    }

    /** Must be called inside a write transaction; ordered locks serialize reciprocal requests. */
    public Interaction interaction(String viewer, String targetName) {
        var actor = users.require(viewer);
        var target = users.require(targetName);
        long actorId = actor.getId();
        long targetId = target.getId();
        if (actorId == targetId) {
            throw new BadRequestException("Escolha outra pessoa.");
        }
        graph.lockPair(actorId, targetId);
        if (blocks.blocked(actorId, targetId)
            || !target.isDiscoverable() && !graph.areFriends(actorId, targetId) && !graph.hasConnection(actorId, targetId)) {
            throw new NotFoundException("Perfil não encontrado.");
        }
        return new Interaction(actorId, targetId);
    }
}
