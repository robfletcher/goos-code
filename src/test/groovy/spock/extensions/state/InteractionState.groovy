package spock.extensions.state

class InteractionState<E extends Enum> {

	private E currentState

	InteractionState(E initialState) {
		this.currentState = initialState
	}

	Closure becomes(E newState) {
		return { currentState = newState }
	}

	Closure when(E expectedState) {
		return { assert currentState == expectedState }
	}

}
