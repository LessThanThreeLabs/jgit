package org.eclipse.jgit.lib;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.eclipse.jgit.lib.Ref.Storage;
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
				Field directory = refDirectory.getClass().getDeclaredField(
						"gitDir");
				directory.setAccessible(true);
				Ref ref = RefUpdateTranslator.translateRef(refUpdate.getRef(),
						userId, (File) directory.get(refDirectory));
				RefDirectoryUpdate newRefUpdate = new RefDirectoryUpdate(
						refDirectory, ref);
				newRefUpdate.setNewObjectId(refUpdate.getNewObjectId());
				newRefUpdate.setForceUpdate(true);
				return newRefUpdate;
			} catch (IllegalArgumentException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (SecurityException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (NoSuchFieldException e) {
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

	private static Ref translateRef(Ref originalRef, long userId, File gitDir) {
		// TODO: Shell out to python here
		final String targetRef;
		if (originalRef.getName().startsWith("refs/heads/")) {
			String output = RefUpdateTranslator.getOutputForCommand(
					"store-pending-and-trigger-build", userId,
					gitDir.getAbsolutePath(),
					"supposed to be a commit message", originalRef.getName());
			targetRef = output;
			return new ObjectIdRef.Unpeeled(Storage.NEW, targetRef,
					originalRef.getObjectId());
		} else {
			String output = RefUpdateTranslator.getOutputForCommand(
					"verify-repository-permissions", userId,
					gitDir.getAbsolutePath());
			if (originalRef.getName().startsWith("refs/force/")) {
				targetRef = "refs/heads/"
						+ originalRef.getName().substring(
						"refs/force/".length());
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
