package com.intellij.localvcs;

import org.junit.Ignore;
import org.junit.Test;

public class LocalVcsBasicsTest extends LocalVcsTestCase {
  // todo clean up LocalVcs tests
  private LocalVcs vcs = new TestLocalVcs();

  @Test
  public void testClearingChangesOnEachChange() {
    vcs.createFile("file", b("content"), -1);
    assertTrue(vcs.isClean());
  }

  @Test
  public void testClearingChangesAfterChangeSetFinifhed() {
    vcs.beginChangeSet();

    vcs.createFile("file", b("content"), -1);
    assertFalse(vcs.isClean());

    vcs.endChangeSet(null);
    assertTrue(vcs.isClean());
  }

  @Test
  public void testApplyingChangesRightAfterChange() {
    vcs.createFile("file", b("content"), -1);
    assertEquals(c("content"), vcs.getEntry("file").getContent());

    vcs.changeFileContent("file", b("new content"), -1);
    assertEquals(c("new content"), vcs.getEntry("file").getContent());
  }

  @Test
  public void testIncrementingIdOnEntryCreation() {
    vcs.createDirectory("dir");
    vcs.createFile("file", null, -1);

    int id1 = vcs.getEntry("dir").getId();
    int id2 = vcs.getEntry("file").getId();

    assertTrue(id2 > id1);
  }

  @Test
  @Ignore("unignore when service states will be completed")
  public void testStartingShangeSetTwiceThrowsException() {
    vcs.beginChangeSet();
    try {
      vcs.beginChangeSet();
      fail();
    }
    catch (IllegalStateException e) {
    }
  }

  @Test
  @Ignore("unignore when service states will be completed")
  public void testFinishingChangeSetWithoutStartingItThrowsException() {
    try {
      vcs.endChangeSet(null);
      fail();
    }
    catch (IllegalStateException e) {
    }
  }

  @Test
  public void testCreatingFile() {
    vcs.createFile("file", b("content"), 123L);

    Entry e = vcs.findEntry("file");

    assertNotNull(e);
    assertEquals(c("content"), e.getContent());
    assertEquals(123L, e.getTimestamp());
  }

  @Test
  public void testCreatingLongFiles() {
    vcs.createFile("file", new byte[IContentStorage.MAX_CONTENT_LENGTH + 1], 777L);

    Entry e = vcs.findEntry("file");

    assertNotNull(e);
    assertEquals(UnavailableContent.class, e.getContent().getClass());
    assertEquals(777L, e.getTimestamp());
  }

  @Test
  public void testCreatingDirectory() {
    vcs.createDirectory("dir");

    Entry e = vcs.findEntry("dir");
    assertNotNull(e);
    assertTrue(e.isDirectory());
  }

  @Test
  public void testCreatingFileUnderDirectory() {
    vcs.createDirectory("dir");
    vcs.createFile("dir/file", null, -1);

    assertTrue(vcs.hasEntry("dir/file"));
  }

  @Test
  public void testAskingForCreatedFileDuringChangeSet() {
    vcs.beginChangeSet();
    vcs.createFile("file", b("content"), -1);

    Entry e = vcs.findEntry("file");

    assertNotNull(e);
    assertEquals(c("content"), e.getContent());
  }

  @Test
  public void testChangingFileContent() {
    vcs.createFile("file", b("content"), -1);
    assertEquals(c("content"), vcs.getEntry("file").getContent());

    vcs.changeFileContent("file", b("new content"), -1);
    assertEquals(c("new content"), vcs.getEntry("file").getContent());
  }

  @Test
  public void testRenamingFile() {
    vcs.createFile("file", null, -1);
    assertTrue(vcs.hasEntry("file"));

    vcs.rename("file", "new file");

    assertFalse(vcs.hasEntry("file"));
    assertTrue(vcs.hasEntry("new file"));
  }

  @Test
  public void testRenamingDirectoryWithContent() {
    vcs.createDirectory("dir1");
    vcs.createDirectory("dir1/dir2");
    vcs.createFile("dir1/dir2/file", null, -1);

    vcs.rename("dir1/dir2", "new dir");

    assertTrue(vcs.hasEntry("dir1/new dir"));
    assertTrue(vcs.hasEntry("dir1/new dir/file"));

    assertFalse(vcs.hasEntry("dir1/dir2"));
  }

  @Test
  public void testTreatingRenamedAndCreatedFilesWithSameNameDifferently() {
    vcs.createFile("file1", null, -1);
    vcs.rename("file1", "file2");
    vcs.createFile("file1", null, -1);

    Entry one = vcs.getEntry("file1");
    Entry two = vcs.getEntry("file2");

    assertNotSame(one, two);
  }

  @Test
  public void testMovingFileFromOneDirectoryToAnother() {
    vcs.createDirectory("dir1");
    vcs.createDirectory("dir2");
    vcs.createFile("dir1/file", null, -1);

    vcs.move("dir1/file", "dir2");

    assertTrue(vcs.hasEntry("dir2/file"));
    assertFalse(vcs.hasEntry("dir1/file"));
  }

  @Test
  public void testMovingDirectory() {
    vcs.createDirectory("root1");
    vcs.createDirectory("root2");
    vcs.createDirectory("root1/dir");
    vcs.createFile("root1/dir/file", null, -1);

    vcs.move("root1/dir", "root2");

    assertTrue(vcs.hasEntry("root2/dir"));
    assertTrue(vcs.hasEntry("root2/dir/file"));
    assertFalse(vcs.hasEntry("root1/dir"));
  }

  @Test
  public void testDeletingFile() {
    vcs.createFile("file", b("content"), -1);
    assertTrue(vcs.hasEntry("file"));

    vcs.delete("file");
    assertFalse(vcs.hasEntry("file"));
  }

  @Test
  public void testDeletingDirectoryWithContent() {
    vcs.createDirectory("dir1");
    vcs.createDirectory("dir1/dir2");
    vcs.createFile("dir1/file1", b("content1"), -1);
    vcs.createFile("dir1/dir2/file2", b("content2"), -1);

    vcs.delete("dir1");
    assertFalse(vcs.hasEntry("dir1"));
    assertFalse(vcs.hasEntry("dir1/dir2"));
    assertFalse(vcs.hasEntry("dir1/file1"));
    assertFalse(vcs.hasEntry("dir1/dir2/file2"));
  }

  public void testDeletingAndAddingSameFile() {
    vcs.createFile("file", null, -1);
    vcs.delete("file");
    vcs.createFile("file", null, -1);

    assertTrue(vcs.hasEntry("file"));
  }

  @Test
  public void testTreatingDeletedAndCreatedFilesWithSameNameDifferently() {
    vcs.createFile("file", null, -1);

    Entry one = vcs.getEntry("file");

    vcs.delete("file");
    vcs.createFile("file", null, -1);

    Entry two = vcs.getEntry("file");

    assertNotSame(one, two);
  }

  @Test
  public void testCreatingRoots() {
    vcs.createDirectory("c:/dir/root");

    assertTrue(vcs.hasEntry("c:/dir/root"));
    assertFalse(vcs.hasEntry("c:/dir"));

    assertEquals("c:/dir/root", vcs.getRoots().get(0).getName());
  }

  @Test
  public void testCreatingFilesUnderRoots() {
    vcs.createDirectory("c:/dir/root");
    vcs.createFile("c:/dir/root/file", null, -1);

    assertTrue(vcs.hasEntry("c:/dir/root/file"));
  }
}