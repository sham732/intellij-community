package com.intellij.localvcs;

import static com.intellij.localvcs.Difference.Kind.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DirectoryEntry extends Entry {
  private List<Entry> myChildren;

  public DirectoryEntry(int id, String name) {
    super(id, name);
    myChildren = new ArrayList<Entry>(3);
  }

  public DirectoryEntry(Stream s) throws IOException {
    super(s);
    int count = s.readInteger();
    myChildren = new ArrayList<Entry>(count);
    while (count-- > 0) {
      unsafeAddChild(s.readEntry());
    }
  }

  @Override
  public void write(Stream s) throws IOException {
    super.write(s);
    s.writeInteger(myChildren.size());
    for (Entry child : myChildren) {
      s.writeEntry(child);
    }
  }

  protected IdPath getIdPathAppendedWith(int id) {
    return getIdPath().appendedWith(id);
  }

  protected String getPathAppendedWith(String name) {
    return Paths.appended(getPath(), name);
  }

  @Override
  public boolean isDirectory() {
    return true;
  }

  @Override
  public void addChild(Entry child) {
    checkDoesNotExist(child);
    unsafeAddChild(child);
  }

  private void unsafeAddChild(Entry child) {
    myChildren.add(child);
    child.setParent(this);
  }

  private void checkDoesNotExist(Entry child) {
    if (findChild(child.getName()) == null) return;

    String m = "entry '%s' already exists in '%s'";
    throw new RuntimeException(String.format(m, child.getName(), getPath()));
  }

  @Override
  public void removeChild(Entry child) {
    // todo we should remove child by name!!!
    myChildren.remove(child);
    child.setParent(null);
  }

  @Override
  public List<Entry> getChildren() {
    return myChildren;
  }

  @Override
  public DirectoryEntry copy() {
    DirectoryEntry result = copyEntry();
    for (Entry child : myChildren) {
      result.unsafeAddChild(child.copy());
    }
    return result;
  }

  protected DirectoryEntry copyEntry() {
    return new DirectoryEntry(myId, myName);
  }

  @Override
  public Difference getDifferenceWith(Entry right) {
    DirectoryEntry e = (DirectoryEntry)right;

    Difference.Kind kind = myName.equals(e.myName) ? NOT_MODIFIED : MODIFIED;
    Difference result = new Difference(false, kind, this, e);

    addCreatedChildrenDifferences(e, result);
    addDeletedChildrenDifferences(e, result);
    addModifiedChildrenDifference(e, result);

    return result;
  }

  private void addCreatedChildrenDifferences(DirectoryEntry e, Difference d) {
    for (Entry child : e.myChildren) {
      if (findDirectChild(child.getId()) == null) {
        d.addChild(child.asCreatedDifference());
      }
    }
  }

  private void addDeletedChildrenDifferences(DirectoryEntry e, Difference d) {
    for (Entry child : myChildren) {
      if (e.findDirectChild(child.getId()) == null) {
        d.addChild(child.asDeletedDifference());
      }
    }
  }

  private void addModifiedChildrenDifference(DirectoryEntry e, Difference d) {
    for (Entry myChild : myChildren) {
      Entry itsChild = e.findDirectChild(myChild.getId());
      if (itsChild != null) {
        Difference childDiff = myChild.getDifferenceWith(itsChild);
        if (childDiff.hasDifference()) d.addChild(childDiff);
      }
    }
  }

  protected Entry findDirectChild(int id) {
    for (Entry child : getChildren()) {
      if (child.getId() == id) return child;
    }
    return null;
  }

  @Override
  protected Difference asCreatedDifference() {
    Difference d = new Difference(false, CREATED, null, this);
    for (Entry child : myChildren) {
      d.addChild(child.asCreatedDifference());
    }
    return d;
  }

  @Override
  protected Difference asDeletedDifference() {
    Difference d = new Difference(false, DELETED, this, null);
    for (Entry child : myChildren) {
      d.addChild(child.asDeletedDifference());
    }
    return d;
  }
}
