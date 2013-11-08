package com.github.sommeri.less4j.core.compiler.selectors;

import java.util.ArrayList;
import java.util.List;

import com.github.sommeri.less4j.core.ast.ASTCssNode;
import com.github.sommeri.less4j.core.ast.Selector;
import com.github.sommeri.less4j.core.ast.SelectorPart;
import com.github.sommeri.less4j.utils.ArraysUtils;
import com.github.sommeri.less4j.utils.ListsComparator;
import com.github.sommeri.less4j.utils.ListsComparator.MatchMarker;

public class SelectorsComparatorForExtend {

  private SelectorsManipulator manipulator = new SelectorsManipulator();
  private SelectorsComparatorUtils utils = new SelectorsComparatorUtils();
  private ListsComparator listsComparator = new ListsComparator();

  private ElementSubsequentComparator elementSubsequentComparator;
  private SimpleSelectorComparator simpleSelectorComparator;
  private SelectorPartComparator selectorPartsComparator;

  public SelectorsComparatorForExtend(GeneralComparatorForExtend generalComparator) {
    this.elementSubsequentComparator = new ElementSubsequentComparator(generalComparator, utils);
    this.simpleSelectorComparator = new SimpleSelectorComparator(elementSubsequentComparator, utils);
    this.selectorPartsComparator = new SelectorPartComparator(simpleSelectorComparator);
  }

  public boolean equals(Selector first, Selector second) {
    List<SelectorPart> firstParts = first.getParts();
    List<SelectorPart> secondParts = second.getParts();
    return listsComparator.equals(firstParts, secondParts, selectorPartsComparator);
  }

  public boolean contains(Selector lookFor, Selector inSelector) {
    List<SelectorPart> lookForParts = lookFor.getParts();
    List<SelectorPart> inSelectorParts = inSelector.getParts();

    return containsInList(lookForParts, inSelectorParts) || containsEmbedded(lookForParts, inSelectorParts);
  }

  public Selector replace(Selector lookFor, Selector inSelector, Selector replaceBy) {
    List<SelectorPart> lookForParts = lookFor.getParts();
    List<SelectorPart> inSelectorParts = ArraysUtils.deeplyClonedList(inSelector.getParts());

    Selector result = new Selector(inSelector.getUnderlyingStructure(), inSelectorParts);
    replaceInList(lookForParts, result, replaceBy.getParts());
    //replaceEmbedded(lookForParts, result); //FIXME: (!!!!) replace in embedded || ;
    return result;
  }

  private boolean containsInList(List<SelectorPart> lookForParts, List<SelectorPart> inSelectorParts) {
    boolean contains = listsComparator.contains(lookForParts, inSelectorParts, selectorPartsComparator);
    return contains;
  }

  //FIXME: (!!!) test with combinator before and in the end
  //FIXME (!!!) document less.js ignores leading and final combinators
  private Selector replaceInList(List<SelectorPart> lookForParts, Selector inSelector, List<SelectorPart> replaceBy) {
    List<SelectorPart> inSelectorParts = inSelector.getParts();
    List<SelectorPart> newInSelectorParts = new ArrayList<SelectorPart>();

    List<MatchMarker<SelectorPart>> matches = listsComparator.findMatches(lookForParts, inSelectorParts, selectorPartsComparator);
    if (matches.isEmpty() || replaceBy == null || replaceBy.isEmpty())
      return null;

    //underlying
    lookForParts = ArraysUtils.deeplyClonedList(lookForParts);
    replaceBy = ArraysUtils.deeplyClonedList(replaceBy);

    SelectorPart firstMatch = matches.get(0).getFirst();
    SelectorPart lastMatch = matches.get(0).getLast();

    if (firstMatch == lastMatch) {
      if (lookForParts.size() != 1)
        throw new IllegalStateException("WTF");

      List<SelectorPart> replaceInside = replaceInside(lookForParts.get(0), lastMatch, replaceBy);
      int indexOfLastMatch = inSelectorParts.indexOf(lastMatch);
      inSelectorParts.remove(lastMatch);
      replaceInside.remove(null); //FIXME (!!!) hack there may be more than that
      inSelectorParts.addAll(indexOfLastMatch, replaceInside);

      inSelector.configureParentToAllChilds();
      return inSelector;
    }

    newInSelectorParts.addAll(chopUpTo(inSelectorParts, firstMatch));
    chopPrefix(inSelectorParts, 1);
    SelectorPart firstRemainder = selectorPartsComparator.cutSuffix(lookForParts.get(0), firstMatch);
    if (firstRemainder != null) {
      SelectorPart firstReplaceBy = replaceBy.remove(0);
      manipulator.directlyJoinParts(firstRemainder, firstReplaceBy);
      newInSelectorParts.add(firstRemainder);
    }

    newInSelectorParts.addAll(replaceBy);

    //FIXME (!!!) extract to some ast manipulator - it is also in simple selector comparator
    removeFromParent(chopUpTo(inSelectorParts, lastMatch));

    SelectorPart lastRemainder = lastMatch == firstMatch ? null : selectorPartsComparator.cutPrefix(ArraysUtils.last(lookForParts), lastMatch);
    //SelectorPart lastRemainder = selectorPartsComparator.cutPrefix(ArraysUtils.last(lookForParts), lastMatch);
    chopPrefix(inSelectorParts, 1);
    if (lastRemainder != null) {
      SelectorPart attachToPart = ArraysUtils.last(newInSelectorParts);
      manipulator.directlyJoinParts(attachToPart, lastRemainder);
    }
    newInSelectorParts.addAll(inSelectorParts);

    inSelector.setParts(newInSelectorParts);
    inSelector.configureParentToAllChilds();

    return inSelector;
  }

//FIXME (!!!!) test and refactor
  private List<SelectorPart> replaceInside(SelectorPart lookFor, SelectorPart inside, List<SelectorPart> replaceBy) {
    SelectorPart[] split = selectorPartsComparator.splitOn(lookFor, inside);
    List<SelectorPart> result = new ArrayList<SelectorPart>();
    SelectorPart previous = null;
    for (int i = 0; i < split.length; i++) {
      List<SelectorPart> replaceByClone = ArraysUtils.deeplyClonedList(replaceBy); 
      if (split[i] != null) {
        if (previous == null)
          result.add(split[i]);
        else
          manipulator.directlyJoinParts(previous, split[i]);
        
        if (i != split.length - 1) {
          manipulator.directlyJoinParts(split[i], replaceByClone.remove(0));
        }
      } 
      
      if (i != split.length - 1) {
        result.addAll(replaceByClone); 
      }
      previous = ArraysUtils.chopLast(replaceByClone);
    }
    return result;
  }

  private List<SelectorPart> chopUpTo(List<SelectorPart> list, SelectorPart exclusiveTo) {
    int indx = list.indexOf(exclusiveTo);
    if (indx == -1)
      return new ArrayList<SelectorPart>();

    List<SelectorPart> subList = list.subList(0, indx);
    List<SelectorPart> result = new ArrayList<SelectorPart>(subList);
    subList.clear();
    return result;
  }

  private List<SelectorPart> chopPrefix(List<SelectorPart> list, int exclusiveTo) {
    if (exclusiveTo > list.size())
      exclusiveTo = list.size();

    List<SelectorPart> subList = list.subList(0, exclusiveTo);
    List<SelectorPart> result = new ArrayList<SelectorPart>(subList);
    subList.clear();
    return result;
  }

  private void removeFromParent(List<SelectorPart> removeThese) {
    for (SelectorPart elementSubsequent : removeThese) {
      elementSubsequent.setParent(null);
    }
    removeThese.clear();
  }

  private List<SelectorPart> sublistCopy(List<SelectorPart> list, int first, int last) {
    return (first > last) ? new ArrayList<SelectorPart>() : new ArrayList<SelectorPart>(list.subList(first, last));
  }

  private boolean containsEmbedded(List<SelectorPart> lookFor, List<SelectorPart> inSelectors) {
    for (SelectorPart inside : inSelectors) {
      if (containsEmbedded(lookFor, inside))
        return true;
    }
    return false;
  }

  private boolean containsEmbedded(List<SelectorPart> lookFor, ASTCssNode inside) {
    for (ASTCssNode kid : inside.getChilds()) {
      switch (kid.getType()) {
      case SELECTOR:
        Selector kidSelector = (Selector) kid;
        if (containsInList(lookFor, kidSelector.getParts()))
          return true;
        break;
      default:
        if (containsEmbedded(lookFor, kid))
          return true;
      }
    }
    return false;
  }

}