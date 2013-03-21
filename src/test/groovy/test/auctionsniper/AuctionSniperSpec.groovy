package test.auctionsniper

import auctionsniper.*
import auctionsniper.AuctionEventListener.PriceSource
import spock.extensions.state.StateMachine
import spock.lang.Specification
import static auctionsniper.SniperState.*

class AuctionSniperSpec extends Specification {

	protected static final String ITEM_ID = "item-id"
	public static final UserRequestListener.Item ITEM = new UserRequestListener.Item(ITEM_ID, 1234)
	def sniperState = new StateMachine<SniperState>("sniper", JOINING)
	def auction = Mock(Auction)
	def sniperListener = Mock(SniperListener)
	def sniper = new AuctionSniper(ITEM, auction)

	void setup() {
		sniper.addSniperListener sniperListener
	}

	void "has initial state of joining"() {
		expect:
		with(sniper.snapshot) {
			itemId == expected.itemId
			lastBid == expected.lastBid
			lastPrice == expected.lastPrice
			state == expected.state
		}

		where:
		expected = SniperSnapshot.joining(ITEM_ID)
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

	void "does not bid and reports losing if first price is above stop price"() {
		when:
		sniper.currentPrice(price, increment, PriceSource.FromOtherBidder)

		then:
		(1.._) * sniperListener.sniperStateChanged(new SniperSnapshot(ITEM_ID, price, 0, LOSING))

		where:
		price = 1233
		increment = 25
	}

	void "does not bid and reports losing if subsequent price is above stop price"() {
		given:
		allowingSniperBidding()

		when:
		sniper.currentPrice(123, 45, PriceSource.FromOtherBidder)
		sniper.currentPrice(2345, 25, PriceSource.FromOtherBidder)

		then:
		(1.._) * sniperListener.sniperStateChanged(new SniperSnapshot(ITEM_ID, 2345, bid, LOSING)) >> {
			sniperState.is(BIDDING)
		}

		where:
		bid = 123 + 45
	}

	void "does not bid and reports losing if price after winning is above stop price"() {
		given:
		allowingSniperBidding()
		allowingSniperWinning()

		when:
		sniper.currentPrice(123, 45, PriceSource.FromOtherBidder)
		sniper.currentPrice(168, 45, PriceSource.FromSniper)
		sniper.currentPrice(price, increment, PriceSource.FromOtherBidder)

		then:
		(1.._) * sniperListener.sniperStateChanged(new SniperSnapshot(ITEM_ID, price, bid, LOSING)) >> {
			sniperState.is(WINNING)
		}

		where:
		price = 1233
		increment = 25
		bid = 123 + 45
	}

	void "continues to be losing once stop price has been reached"() {
		when:
		sniper.currentPrice(price1, 25, PriceSource.FromOtherBidder)
		sniper.currentPrice(price2, 25, PriceSource.FromOtherBidder)

		then:
		(1.._) * sniperListener.sniperStateChanged(new SniperSnapshot(ITEM_ID, price1, 0, LOSING))

		then:
		(1.._) * sniperListener.sniperStateChanged(new SniperSnapshot(ITEM_ID, price2, 0, LOSING))

		where:
		price1 = 1233
		price2 = 1258
	}

	void "reports lost if auction closes when bidding"() {
		given:
		allowingSniperBidding()

		when:
		sniper.currentPrice(123, 45, PriceSource.FromOtherBidder)
		sniper.auctionClosed()

		then:
		(1.._) * sniperListener.sniperStateChanged(new SniperSnapshot(ITEM_ID, 123, 168, LOST)) >> {
			sniperState.is(BIDDING)
		}
	}

	void "reports lost if auction closes when losing"() {
		given:
		allowingSniperLosing()

		when:
		sniper.currentPrice(1230, 456, PriceSource.FromOtherBidder)
		sniper.auctionClosed()

		then:
		(1.._) * sniperListener.sniperStateChanged(new SniperSnapshot(ITEM_ID, 1230, 0, LOST)) >> {
			sniperState.is(LOSING)
		}
	}

	void "reports is winning when current price comes from sniper"() {
		given:
		allowingSniperBidding()

		when:
		sniper.currentPrice(123, 12, PriceSource.FromOtherBidder)
		sniper.currentPrice(135, 45, PriceSource.FromSniper)

		then:
		(1.._) * sniperListener.sniperStateChanged(new SniperSnapshot(ITEM_ID, 135, 135, WINNING)) >> {
			sniperState.is(BIDDING)
		}
	}

	void "reports won if auction closes when winning"() {
		given:
		allowingSniperBidding()
		allowingSniperWinning()

		when:
		sniper.currentPrice(123, 12, PriceSource.FromOtherBidder)
		sniper.currentPrice(135, 45, PriceSource.FromSniper)
		sniper.auctionClosed()

		then:
		(1.._) * sniperListener.sniperStateChanged(new SniperSnapshot(ITEM_ID, 135, 135, WON)) >> {
			sniperState.is(WINNING)
		}
	}

	private void allowingSniperBidding() {
		allowSniperStateChange(BIDDING)
	}

	private void allowingSniperWinning() {
		allowSniperStateChange(WINNING)
	}

	private void allowingSniperLosing() {
		allowSniperStateChange(LOSING)
	}

	private void allowSniperStateChange(SniperState newState) {
		sniperListener.sniperStateChanged({ it.state == newState }) >> { sniperState.becomes(newState) }
	}
}
