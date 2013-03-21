package test.auctionsniper

import auctionsniper.*
import auctionsniper.AuctionEventListener.PriceSource
import spock.lang.Specification
import static auctionsniper.SniperState.*

class AuctionSniperSpec extends Specification {

	protected static final String ITEM_ID = "item-id"
	public static final UserRequestListener.Item ITEM = new UserRequestListener.Item(ITEM_ID, 1234)
//	private final States sniperState = context.states("sniper");
	def auction = Mock(Auction)
	def sniperListener = Mock(SniperListener)
	def sniper = new AuctionSniper(ITEM, auction)

	void setup() {
		sniper.addSniperListener sniperListener
	}

	void "has initial state of joining"() {
		expect:
		sniper.getSnapshot() samePropertyValuesAs(SniperSnapshot.joining(ITEM_ID))
	}

	void "reports lost when auction closes immediately"() {
		when:
		sniper.auctionClosed()

		then:
		(1.._) * sniperListener.sniperStateChanged(new SniperSnapshot(ITEM_ID, 0, 0, LOST))
	}

	void "bids higher and reports bidding when new price arrives"() {
		when:
		sniper.currentPrice(price, increment, PriceSource.FromOtherBidder)

		then:
		1 * auction.bid(bid)
		(1.._) * sniperListener.sniperStateChanged(new SniperSnapshot(ITEM_ID, price, bid, BIDDING))

		where:
		price = 1001
		increment = 25
		bid = price + increment
	}

}
