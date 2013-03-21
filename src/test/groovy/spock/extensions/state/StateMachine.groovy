package spock.extensions.state

class StateMachine<E extends Enum> {

	private final String name
	private E currentState

	StateMachine(String name, E initialState) {
		this.name = name
		currentState = initialState
	}

	void becomes(E newState) {
		currentState = newState
	}

	void is(E expectedState) {
		assert currentState == expectedState, "$name state should be $expectedState but is $currentState"
	}

	void isNot(E expectedState) {
		assert currentState != expectedState, "$name state should not be $expectedState but it is"
	}

}
