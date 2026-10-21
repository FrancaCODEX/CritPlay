package br.com.critplay.social;

import br.com.critplay.profile.PublicProfileService;
import br.com.critplay.shared.BadRequestException;
import br.com.critplay.shared.NotFoundException;
import br.com.critplay.users.UserRepository;
import br.com.critplay.users.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import br.com.critplay.social.SocialGraphRepository.FriendshipState;

/** Social use cases. SQL, access decisions and public-profile rendering have separate owners. */
@Service
public class SocialService {
    private final UserService users;
    private final UserRepository repository;
    private final SocialGraphRepository graph;
    private final SocialAccessService access;
    private final PublicProfileService profiles;

    public SocialService(UserService users, UserRepository repository, SocialGraphRepository graph,
                         SocialAccessService access, PublicProfileService profiles) {
        this.users = users;
        this.repository = repository;
        this.graph = graph;
        this.access = access;
        this.profiles = profiles;
    }

    public List<Map<String, Object>> search(String viewer, String query) {
        long viewerId = users.require(viewer).getId();
        String text = query.strip().toLowerCase(Locale.ROOT);
        if (text.length() < 2) return List.of();
        if (text.length() > 80) throw new BadRequestException("Busque por até 80 caracteres.");
        String literal = text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return graph.search(viewerId, "%" + literal + "%").stream().map(PersonIdentity::toMap).toList();
    }

    public Map<String, Object> profile(String viewer, String targetName) {
        return profiles.get(viewer, targetName);
    }

    public Map<String, Object> ownSocial(String viewer) {
        long owner = users.require(viewer).getId();
        return graph.counts(owner, owner);
    }

    public Map<String, Object> followPage(String viewer, String targetName, boolean followers, long after) {
        if (after < 0) throw new BadRequestException("Página inválida.");
        var permission = access.profile(viewer, targetName);
        if (!permission.visible()) throw new NotFoundException("Lista não disponível.");
        return graph.followPage(permission.viewerId(), permission.target().getId(), followers, after);
    }

    public boolean friends(Long first, Long second) {
        return graph.areFriends(first, second);
    }

    public Map<String, Object> connections(String viewer) {
        long owner = users.require(viewer).getId();
        var friends = new ArrayList<Map<String, Object>>();
        var incoming = new ArrayList<Map<String, Object>>();
        var outgoing = new ArrayList<Map<String, Object>>();
        for (var friendship : graph.friendships(owner)) {
            var destination = friendship.accepted() ? friends
                : friendship.requesterId() == owner ? outgoing : incoming;
            destination.add(friendship.person().toMap());
        }
        return Map.of(
            "following", graph.connections(owner, false).stream().map(PersonIdentity::toMap).toList(),
            "followers", graph.connections(owner, true).stream().map(PersonIdentity::toMap).toList(),
            "friends", friends, "incoming", incoming, "outgoing", outgoing);
    }

    @Transactional
    public Map<String, Object> privacy(String viewer, ProfileVisibility visibility, boolean discoverable) {
        return privacy(viewer, visibility, discoverable, null, null, null, null);
    }

    @Transactional
    public Map<String, Object> privacy(String viewer, ProfileVisibility visibility, boolean discoverable,
                                       Boolean library, Boolean favorites, Boolean wishlist, Boolean lists) {
        var user = users.require(viewer);
        user.updatePrivacy(visibility, discoverable);
        user.updateCollections(library, favorites, wishlist, lists);
        repository.save(user);
        return Map.of("visibility", user.getVisibility(), "discoverable", user.isDiscoverable(),
            "collectionPrivacy", user.collectionPrivacy());
    }

    @Transactional
    public Map<String, Object> follow(String viewer, String targetName, boolean following) {
        var pair = access.interaction(viewer, targetName);
        if (following) {
            requireEnabled(pair.targetId());
            graph.follow(pair.actorId(), pair.targetId());
        } else {
            graph.unfollow(pair.actorId(), pair.targetId());
        }
        return graph.relationship(pair.actorId(), pair.targetId()).toMap();
    }

    @Transactional
    public Map<String, Object> friendship(String viewer, String targetName, String action) {
        var pair = access.interaction(viewer, targetName);
        long actor = pair.actorId();
        long target = pair.targetId();
        if ("REQUEST".equals(action) || "ACCEPT".equals(action)) requireEnabled(target);
        var current = graph.relationship(actor, target).friendship();
        switch (action) {
            case "REQUEST" -> {
                if (current == FriendshipState.NONE) graph.requestFriendship(actor, target);
                else if (current == FriendshipState.INCOMING) {
                    throw new BadRequestException("Você já recebeu um pedido desta pessoa. Aceite ou recuse.");
                }
            }
            case "ACCEPT" -> {
                requireState(current, FriendshipState.INCOMING, "Você não tem esse pedido para aceitar.");
                graph.acceptFriendship(actor, target);
            }
            case "REJECT" -> {
                requireState(current, FriendshipState.INCOMING, "Você não tem esse pedido para recusar.");
                graph.removeFriendship(actor, target);
            }
            case "CANCEL" -> {
                requireState(current, FriendshipState.OUTGOING, "Você não tem esse pedido para cancelar.");
                graph.removeFriendship(actor, target);
            }
            case "REMOVE" -> {
                requireState(current, FriendshipState.FRIENDS, "Essa amizade não existe.");
                graph.removeFriendship(actor, target);
            }
            default -> throw new BadRequestException("Ação de amizade inválida.");
        }
        return graph.relationship(actor, target).toMap();
    }

    private void requireEnabled(long user) {
        if (!graph.isEnabled(user)) throw new NotFoundException("Perfil não encontrado.");
    }

    private void requireState(FriendshipState current, FriendshipState expected, String message) {
        if (current != expected) throw new BadRequestException(message);
    }
}
