package spock.extensions.state

class StateMachine<E extends Enum> {

	private final String name
	private E currentState

	StateMachine(String name) {
		this.name = name
	}

	void startsAs(E initialState) {
		if (currentState) throw new IllegalStateException()
		currentState = initialState
	}

	void becomes(E newState) {
		currentState = newState
	}

	void is(E expectedState) {
		assert currentState == expectedState, "$name state should be $expectedState but is $currentState"
	}

}
