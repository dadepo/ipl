package ibis.satin.impl;

public abstract class APIMethods extends Malleability {
	/* ------------------- pause/resume stuff ---------------------- */

	/**
	 * Pause Satin operation. This method can optionally be called before a
	 * large sequential part in a program. This will temporarily pause Satin's
	 * internal load distribution strategies to avoid communication overhead
	 * during sequential code.
	 */
	public static void pause() {
		if (this_satin == null || !this_satin.upcalls)
			return;
		this_satin.receivePort.disableUpcalls();
	}

	/**
	 * Resume Satin operation. This method can optionally be called after a
	 * large sequential part in a program.
	 */
	public static void resume() {
		if (this_satin == null || !this_satin.upcalls)
			return;
		this_satin.receivePort.enableUpcalls();
	}

	/**
	 * Returns whether it might be useful to spawn more methods. If there is
	 * enough work in the system to keep all processors busy, this method
	 * returns false.
	 */
	public static boolean needMoreJobs() {
		// This can happen in sequential programs.
		if (this_satin == null) {
			return false;
		}
		synchronized (this_satin) {
			int size = this_satin.victims.size();
			// if(size == 1 && this_satin.closed) return false; // No need to
			// spawn work on one machine.
			// No no, size == 1 means that there is one OTHER
			// machine ... (Ceriel)
			if (size == 0 && this_satin.closed)
				return false; // No need to
			// spawn work on
			// one machine.

			if (this_satin.q.size() / (size + 1) > this_satin.suggestedQueueSize)
				return false;
		}

		return true;
	}

	/**
	 * Returns whether the current method was generated by the machine it is
	 * running on. Methods can be distributed to remote machines by the Satin
	 * runtime system, in which case this method returns false.
	 */
	public static boolean localJob() {
		if (this_satin == null)
			return true; // sequential run

		if (this_satin.parentOwner == null)
			return true; // root job

		return this_satin.parentOwner.equals(this_satin.ident);
	}
}
