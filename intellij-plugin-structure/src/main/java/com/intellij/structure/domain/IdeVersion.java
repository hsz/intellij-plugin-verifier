package com.intellij.structure.domain;

import com.intellij.structure.impl.domain.IdeVersionComparator;
import com.intellij.structure.impl.domain.IdeVersionImpl;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

/**
 * @author Sergey Patrikeev
 */
public abstract class IdeVersion {

  public static final Comparator<IdeVersion> VERSION_COMPARATOR = new IdeVersionComparator();

  /**
   * @throws IllegalArgumentException if specified {@code version} doesn't represent correct {@code IdeVersion}
   */
  @NotNull
  public static IdeVersion createIdeVersion(@NotNull String version) throws IllegalArgumentException {
    return new IdeVersionImpl(version);
  }

  /**
   * Returns a string presentation of this version in the form:<p>
   * <b> {@literal [<product_code>-]<branch #>.<build #>[.<SNAPSHOT>|.<attempt #>]}</b> <p>
   * If the version represents a baseline (an integer number, e.g 9567) this number is returned<p>
   * Examples are:
   *   <ul>
   *     <li>IU-143.1532.7</li>
   *     <li>IU-143.1532.SNAPSHOT</li>
   *     <li>143.1532</li>
   *     <li>7341</li>
   *   </ul>
   *
   * @see
   * <blockquote>
   *    <a href="http://www.jetbrains.org/intellij/sdk/docs/basics/getting_started/build_number_ranges.html">
   *    <i>IntelliJ Build Number Ranges</i></a>
   * </blockquote>
   * @return presentation
   */
  public abstract String getFullPresentation();

  @NotNull
  public abstract String getProductCode();

  @NotNull
  public abstract String getProductName();

  public abstract int getBranch();

  public abstract int getBuild();

  public abstract int getAttempt();

  public abstract boolean isSnapshot();

}
