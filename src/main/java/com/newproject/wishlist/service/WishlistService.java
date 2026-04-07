package com.newproject.wishlist.service;

import com.newproject.wishlist.domain.WishlistItem;
import com.newproject.wishlist.dto.WishlistItemRequest;
import com.newproject.wishlist.dto.WishlistItemResponse;
import com.newproject.wishlist.events.EventPublisher;
import com.newproject.wishlist.exception.NotFoundException;
import com.newproject.wishlist.repository.WishlistItemRepository;
import com.newproject.wishlist.security.RequestActor;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WishlistService {
    private final WishlistItemRepository wishlistItemRepository;
    private final EventPublisher eventPublisher;
    private final RequestActor requestActor;

    public WishlistService(WishlistItemRepository wishlistItemRepository, EventPublisher eventPublisher, RequestActor requestActor) {
        this.wishlistItemRepository = wishlistItemRepository;
        this.eventPublisher = eventPublisher;
        this.requestActor = requestActor;
    }

    @Transactional(readOnly = true)
    public List<WishlistItemResponse> listByCustomer(Long customerId) {
        requestActor.assertCustomerAccessIfAuthenticated(customerId);
        return wishlistItemRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Transactional
    public WishlistItemResponse add(Long customerId, WishlistItemRequest request) {
        requestActor.assertCustomerAccessIfAuthenticated(customerId);
        WishlistItem item = wishlistItemRepository.findByCustomerIdAndProductId(customerId, request.getProductId())
            .orElseGet(WishlistItem::new);

        boolean created = item.getId() == null;
        if (created) {
            item.setCustomerId(customerId);
            item.setProductId(request.getProductId());
            item.setCreatedAt(OffsetDateTime.now());
        }

        WishlistItem saved = wishlistItemRepository.save(item);
        eventPublisher.publish(created ? "WISHLIST_ITEM_ADDED" : "WISHLIST_ITEM_EXISTS", "wishlist_item", saved.getId().toString(), toResponse(saved));
        return toResponse(saved);
    }

    @Transactional
    public void remove(Long customerId, Long productId) {
        requestActor.assertCustomerAccessIfAuthenticated(customerId);
        WishlistItem item = wishlistItemRepository.findByCustomerIdAndProductId(customerId, productId)
            .orElseThrow(() -> new NotFoundException("Wishlist item not found"));

        wishlistItemRepository.delete(item);
        eventPublisher.publish("WISHLIST_ITEM_REMOVED", "wishlist_item", item.getId().toString(), null);
    }

    private WishlistItemResponse toResponse(WishlistItem item) {
        WishlistItemResponse response = new WishlistItemResponse();
        response.setId(item.getId());
        response.setCustomerId(item.getCustomerId());
        response.setProductId(item.getProductId());
        response.setCreatedAt(item.getCreatedAt());
        return response;
    }
}
