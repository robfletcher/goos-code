package test.auctionsniper

import auctionsniper.*
import auctionsniper.AuctionEventListener.PriceSource
import org.hamcrest.*
import spock.extensions.state.InteractionState
import spock.lang.Specification
import static auctionsniper.SniperState.*
import static org.hamcrest.Matchers.equalTo

class AuctionSniperSpec extends Specification {

	protected static final String ITEM_ID = "item-id"
	public static final UserRequestListener.Item ITEM = new UserRequestListener.Item(ITEM_ID, 1234)
	def sniperState = new InteractionState<SniperState>(JOINING)
	def auction = Mock(Auction)
	def sniperListener = Mock(SniperListener)
	def sniper = new AuctionSniper(ITEM, auction)

	void setup() {
		sniper.addSniperListener sniperListener
	}

	void "has initial state of joining"() {
		expect:
		with(sniper.getSnapshot()) {
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
		sniperListener.sniperStateChanged(aSniperThatIs(BIDDING)) >> sniperState.becomes(BIDDING)

		when:
		sniper.currentPrice(123, 45, PriceSource.FromOtherBidder)
		sniper.currentPrice(2345, 25, PriceSource.FromOtherBidder)

		then:
		(1.._) * sniperListener.sniperStateChanged(new SniperSnapshot(ITEM_ID, 2345, bid, LOSING)) >> sniperState.when(BIDDING)

		where:
		bid = 123 + 45
	}

	void "does not bid and reports losing if price after winning is above stop price"() {
		given:
		sniperListener.sniperStateChanged(aSniperThatIs(BIDDING)) >> { sniperState = BIDDING }
		sniperListener.sniperStateChanged(aSniperThatIs(WINNING)) >> { sniperState = WINNING }

		when:
		sniper.currentPrice(123, 45, PriceSource.FromOtherBidder)
		sniper.currentPrice(168, 45, PriceSource.FromSniper)
		sniper.currentPrice(price, increment, PriceSource.FromOtherBidder)

		then:
		(1.._) * sniperListener.sniperStateChanged(new SniperSnapshot(ITEM_ID, price, bid, LOSING)) >> { assert sniperState == WINNING }

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
		sniperListener.sniperStateChanged(aSniperThatIs(BIDDING)) >> { sniperState = BIDDING }

		when:
		sniper.currentPrice(123, 45, PriceSource.FromOtherBidder)
		sniper.auctionClosed()

		then:
		(1.._) * sniperListener.sniperStateChanged(new SniperSnapshot(ITEM_ID, 123, 168, LOST)) >> { assert sniperState == BIDDING }
	}

	void "reports lost if auction closes when losing"() {
		given:
		sniperListener.sniperStateChanged(aSniperThatIs(LOSING)) >> { sniperState = LOSING }

		when:
		sniper.currentPrice(1230, 456, PriceSource.FromOtherBidder)
		sniper.auctionClosed()

		then:
		(1.._) * sniperListener.sniperStateChanged(new SniperSnapshot(ITEM_ID, 1230, 0, LOST)) >> { assert sniperState == LOSING }
	}

	void "reports is winning when current price comes from sniper"() {
		given:
		sniperListener.sniperStateChanged(aSniperThatIs(BIDDING)) >> { sniperState = BIDDING }

		when:
		sniper.currentPrice(123, 12, PriceSource.FromOtherBidder)
		sniper.currentPrice(135, 45, PriceSource.FromSniper)

		then:
		(1.._) * sniperListener.sniperStateChanged(new SniperSnapshot(ITEM_ID, 135, 135, WINNING)) >> { assert sniperState == BIDDING }
	}

	void "reports won if auction closes when winning"() {
		given:
		sniperListener.sniperStateChanged(aSniperThatIs(BIDDING)) >> { sniperState = BIDDING }
		sniperListener.sniperStateChanged(aSniperThatIs(WINNING)) >> { sniperState = WINNING }

		when:
		sniper.currentPrice(123, 12, PriceSource.FromOtherBidder)
		sniper.currentPrice(135, 45, PriceSource.FromSniper)
		sniper.auctionClosed()

		then:
		(1.._) * sniperListener.sniperStateChanged(new SniperSnapshot(ITEM_ID, 135, 135, WON)) >> { assert sniperState == WINNING }
	}

	private Matcher<SniperSnapshot> aSniperThatIs(final SniperState state) {
		new FeatureMatcher<SniperSnapshot, SniperState>(equalTo(state), "sniper that is ", "was") {
			@Override
			protected SniperState featureValueOf(SniperSnapshot actual) {
				actual.state
			}
		}
	}
}
