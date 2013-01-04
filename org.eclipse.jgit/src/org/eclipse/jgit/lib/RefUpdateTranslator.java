package org.eclipse.jgit.lib;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Ref.Storage;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.RefDirectory;
import org.eclipse.jgit.storage.file.RefDirectoryUpdate;

/**
 * @author bbland
 *
 *         Horrible class using reflection and other bogus stuff
 *
 */
public class RefUpdateTranslator {
	/**
	 * @param refUpdate
	 * @param userId
	 * @return translated ref update
	 */
	public static RefUpdate translateRefUpdate(RefUpdate refUpdate, long userId) {
		if (refUpdate instanceof RefDirectoryUpdate) {
			try {
				RefDirectory refDirectory = (RefDirectory) RefUpdateTranslator
						.getRefDatabase(refUpdate);
				Method getRepository = refDirectory.getClass().getDeclaredMethod("getRepository");
				getRepository.setAccessible(true);
				Repository repository = (Repository) getRepository
						.invoke(refDirectory);
				Ref ref = RefUpdateTranslator.translateRef(refUpdate,
						userId, repository);
				RefDirectoryUpdate newRefUpdate = new RefDirectoryUpdate(
						refDirectory, ref);
				newRefUpdate.setNewObjectId(refUpdate.getNewObjectId());
				if (refUpdate.getRef().getName().startsWith("refs/force/")) {
					newRefUpdate.setForceUpdate(true);
				}
				return newRefUpdate;
			} catch (RuntimeException e) {
				throw e;
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		throw new IllegalArgumentException();
	}

	private static RefDatabase getRefDatabase(RefUpdate refUpdate) {
		try {
			Method getRefDatabase = refUpdate.getClass().getDeclaredMethod(
					"getRefDatabase");
			getRefDatabase.setAccessible(true);
			return (RefDatabase) getRefDatabase.invoke(refUpdate);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	private static Ref translateRef(RefUpdate refUpdate, long userId,
			Repository repository) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		final String targetRef;
		final Ref originalRef = refUpdate.getRef();
		if (originalRef.getName().startsWith("refs/for/")) {
			final String commitMessage = new RevWalk(repository).parseCommit(
					refUpdate.getNewObjectId()).getFullMessage();
			String output = RefUpdateTranslator.getOutputForCommand(
					"store-pending-and-trigger-build", userId, repository
							.getDirectory().getAbsolutePath(), commitMessage,
					originalRef.getName().substring("refs/for/".length()));
			targetRef = output;
			return new ObjectIdRef.Unpeeled(Storage.NEW, targetRef,
					originalRef.getObjectId());
		} else {
			RefUpdateTranslator.getOutputForCommand(
					"verify-repository-permissions", userId, repository
							.getDirectory().getAbsolutePath());
			if (originalRef.getName().startsWith("refs/force/")) {
				targetRef = originalRef.getName().replace("refs/force/",
						"refs/heads/");
				return new ObjectIdRef.Unpeeled(Storage.NEW, targetRef,
						originalRef.getObjectId());
			}
			return originalRef;
		}
	}

	private static String getOutputForCommand(String command, long userId,
			String... args) {
		String[] fullArgs = new String[2 + args.length];
		fullArgs[0] = command;
		fullArgs[1] = String.valueOf(userId);
		for (int i = 0; i < args.length; i++) {
			fullArgs[2 + i] = args[i];
		}
		ProcessBuilder pb = new ProcessBuilder(fullArgs);
		try {
			Process p = pb.start();
			int returnCode = p.waitFor();
			if (returnCode != 0) {
				throw new IllegalArgumentException(String.valueOf(userId));
			}
			BufferedReader reader = new BufferedReader(new InputStreamReader(
					p.getInputStream()));
			return reader.readLine();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

}
