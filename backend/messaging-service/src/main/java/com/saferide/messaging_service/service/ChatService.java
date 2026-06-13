package com.saferide.messaging_service.service;

import com.saferide.messaging_service.client.ProfileClient;
import com.saferide.messaging_service.client.RideMembers;
import com.saferide.messaging_service.client.RidesClient;
import com.saferide.messaging_service.model.dtos.ChatMessageResponse;
import com.saferide.messaging_service.model.dtos.ChatSummaryResponse;
import com.saferide.messaging_service.model.entity.ChatMessage;
import com.saferide.messaging_service.model.entity.ChatReadState;
import com.saferide.messaging_service.repo.ChatMessageRepository;
import com.saferide.messaging_service.repo.ChatReadStateRepository;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-ride text chat. Messages live here; ride membership is owned by
 * rides-service and checked on every read/write. New messages are
 * broadcast to {@code /topic/chat.<rideId>} so subscribed members get them
 * in real time (the sender included).
 */
@Service
public class ChatService {

    /** STOMP topic a ride's members subscribe to. */
    public static final String TOPIC_PREFIX = "/topic/chat.";

    private final ChatMessageRepository messageRepository;
    private final ChatReadStateRepository readStateRepository;
    private final RidesClient ridesClient;
    private final ProfileClient profileClient;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatService(ChatMessageRepository messageRepository,
                       ChatReadStateRepository readStateRepository,
                       RidesClient ridesClient,
                       ProfileClient profileClient,
                       SimpMessagingTemplate messagingTemplate) {
        this.messageRepository = messageRepository;
        this.readStateRepository = readStateRepository;
        this.ridesClient = ridesClient;
        this.profileClient = profileClient;
        this.messagingTemplate = messagingTemplate;
    }

    /** Persist a message and broadcast it to the ride's topic. */
    public ChatMessageResponse postMessage(UUID rideId, UUID senderId, String text) {
        if (text == null || text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message text is required");
        }
        requireMember(rideId, senderId);

        String trimmed = text.strip();
        if (trimmed.length() > 2000) {
            trimmed = trimmed.substring(0, 2000);
        }
        ChatMessage message = new ChatMessage();
        message.setRideId(rideId);
        message.setSenderId(senderId);
        message.setText(trimmed);
        ChatMessage saved = messageRepository.save(message);

        String senderName = profileClient.resolveNames(List.of(senderId))
                .getOrDefault(senderId, fallbackName(senderId));
        ChatMessageResponse response = new ChatMessageResponse(
                saved.getId(), rideId, senderId, senderName, saved.getText(), saved.getSentAt());

        messagingTemplate.convertAndSend(TOPIC_PREFIX + rideId, response);
        return response;
    }

    /** Full history for a ride; opening it marks the chat read. */
    public List<ChatMessageResponse> getHistory(UUID rideId, UUID userId) {
        requireMember(rideId, userId);
        List<ChatMessage> messages = messageRepository.findByRideIdOrderBySentAtAsc(rideId);
        Map<UUID, String> names = profileClient.resolveNames(
                messages.stream().map(ChatMessage::getSenderId).toList());
        markRead(rideId, userId);
        return messages.stream().map(m -> toResponse(m, names)).toList();
    }

    /** The user's chats (one per ride they're in), newest activity first. */
    public List<ChatSummaryResponse> listChats(UUID userId) {
        List<RideMembers> rides = ridesClient.getUserChats(userId);
        List<UUID> allMemberIds = rides.stream()
                .flatMap(r -> r.memberIds() != null ? r.memberIds().stream() : java.util.stream.Stream.empty())
                .distinct()
                .toList();
        Map<UUID, String> names = profileClient.resolveNames(allMemberIds);

        List<ChatSummaryResponse> summaries = new ArrayList<>();
        for (RideMembers ride : rides) {
            ChatMessage last = messageRepository
                    .findTopByRideIdOrderBySentAtDesc(ride.rideId()).orElse(null);
            Instant lastReadAt = readStateRepository.findByUserIdAndRideId(userId, ride.rideId())
                    .map(ChatReadState::getLastReadAt).orElse(Instant.EPOCH);
            long unread = messageRepository.countByRideIdAndSentAtAfterAndSenderIdNot(
                    ride.rideId(), lastReadAt, userId);

            List<String> memberNames = (ride.memberIds() == null ? List.<UUID>of() : ride.memberIds())
                    .stream()
                    .filter(id -> !id.equals(userId))
                    .map(id -> names.getOrDefault(id, fallbackName(id)))
                    .toList();

            summaries.add(new ChatSummaryResponse(
                    ride.rideId(),
                    buildTitle(ride),
                    memberNames,
                    last != null ? last.getText() : null,
                    last != null ? names.getOrDefault(last.getSenderId(), fallbackName(last.getSenderId())) : null,
                    last != null ? last.getSentAt() : null,
                    unread));
        }
        // Most recent conversation first; chats with no messages sink to the bottom.
        summaries.sort(Comparator.comparing(
                ChatSummaryResponse::lastSentAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return summaries;
    }

    public void markRead(UUID rideId, UUID userId) {
        ChatReadState state = readStateRepository.findByUserIdAndRideId(userId, rideId)
                .orElseGet(() -> {
                    ChatReadState s = new ChatReadState();
                    s.setUserId(userId);
                    s.setRideId(rideId);
                    return s;
                });
        state.setLastReadAt(Instant.now());
        readStateRepository.save(state);
    }

    public long unreadCount(UUID userId) {
        long total = 0;
        for (RideMembers ride : ridesClient.getUserChats(userId)) {
            Instant lastReadAt = readStateRepository.findByUserIdAndRideId(userId, ride.rideId())
                    .map(ChatReadState::getLastReadAt).orElse(Instant.EPOCH);
            total += messageRepository.countByRideIdAndSentAtAfterAndSenderIdNot(
                    ride.rideId(), lastReadAt, userId);
        }
        return total;
    }

    /** Authorization gate used by reads, writes, and the subscribe interceptor. */
    public boolean isMember(UUID rideId, UUID userId) {
        return ridesClient.isMember(rideId, userId);
    }

    private void requireMember(UUID rideId, UUID userId) {
        if (!ridesClient.isMember(rideId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You're not part of this ride's chat");
        }
    }

    private ChatMessageResponse toResponse(ChatMessage m, Map<UUID, String> names) {
        return new ChatMessageResponse(
                m.getId(), m.getRideId(), m.getSenderId(),
                names.getOrDefault(m.getSenderId(), fallbackName(m.getSenderId())),
                m.getText(), m.getSentAt());
    }

    private String buildTitle(RideMembers ride) {
        if (ride.pickup() != null && ride.drop() != null) {
            return ride.pickup() + " → " + ride.drop();
        }
        return "Ride chat";
    }

    private static String fallbackName(UUID userId) {
        return "User " + userId.toString().substring(0, 8);
    }
}
