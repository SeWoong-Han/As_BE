package Auction_shop.auction.domain.bid.service;

import Auction_shop.auction.domain.bid.Bid;
import Auction_shop.auction.domain.bid.repository.BidJpaRepository;
import Auction_shop.auction.domain.bid.repository.BidRedisRepository;
import Auction_shop.auction.domain.product.Product;
import Auction_shop.auction.domain.product.ProductDocument;
import Auction_shop.auction.domain.product.elasticRepository.ProductElasticsearchRepository;
import Auction_shop.auction.domain.product.repository.ProductJpaRepository;
import Auction_shop.auction.web.dto.product.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BidService {
    private final BidRedisRepository bidRedisRepository;
    private final BidJpaRepository bidJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final ProductElasticsearchRepository productElasticsearchRepository;
    private final ProductMapper productMapper;

    public Bid placeBid(Long userId, Long productId, int bidAmount){

        Product product = productJpaRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException(productId + "에 해당하는 물건이 없습니다."));

        if (product.isSold()){
            throw new RuntimeException("이미 판매된 물품입니다.");
        } else if (LocalDateTime.now().isAfter(product.getEndTime())) {
            throw new RuntimeException("경매가 종료되었습니다.");
        } else if (bidAmount <= product.getCurrent_price()) {
            throw new RuntimeException("방금 누군가가 "+bidAmount+"원 이상의 가격으로 입찰을 넣었습니다.");
        }

        product.bidProduct(bidAmount);

        Bid bid = Bid.builder()
                .productId(productId)
                .userId(userId)
                .amount(bidAmount)
                .bidTime(LocalDateTime.now())
                .build();

        bidRedisRepository.save(bid);
        bidJpaRepository.save(bid);

        productJpaRepository.save(product);
        ProductDocument document = productMapper.toDocument(product);
        productElasticsearchRepository.save(document);

        return bid;
    }

    public List<Bid> getBidsForProduct(Long productId){
        List<Bid> bids = bidRedisRepository.findBidsByProductId(productId);

        if (bids.isEmpty()){
            bids = bidJpaRepository.findByProductId(productId);
            for(Bid bid : bids){
                System.out.println("bid.getAmount() = " + bid.getAmount());
                bidRedisRepository.save(bid);
            }
            Collections.reverse(bids);
        }

        return bids;
    }

    public Bid getHighestBidForProduct(Long productId){
        Bid highestBid = bidRedisRepository.findHighestBidByProductId(productId);

        if (highestBid == null){
            highestBid = bidJpaRepository.findHighestBidByProductId(productId);
        }

        return highestBid;
    }
}
