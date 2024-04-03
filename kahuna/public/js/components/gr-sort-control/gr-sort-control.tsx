import * as React from "react";
import * as angular from "angular";
import { react2angular } from "react2angular";
import { useEffect, useState, KeyboardEvent } from "react";

import "./gr-sort-control.css";

const SELECT_OPTION = "Select an option";
const DEFAULT_OPTION = "uploadNewOld";
const COLLECTION_OPTION = "collecAdded";
const CONTROL_TITLE = "Sort by:";

const downArrowIcon = () =>
  <svg width="12" height="12" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
    <path d="M11.178 19.569a.998.998 0 0 0 1.644 0l9-13A.999.999 0 0 0 21 5H3a1.002 1.002 0 0 0-.822 1.569l9 13z"/>
  </svg>;

const emptyIcon = () =>
  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24">
    <rect width="100%" height="100%" fill="none" stroke="none" />
  </svg>;

const tickIcon = () =>
  <svg width="24" height="24" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
    <polyline fill="none" stroke="inherit" points="3.7 14.3 9.6 19 20.3 5" strokeLinecap="round" strokeLinejoin="round" strokeWidth="2"/>
  </svg>;

export interface SortDropdownOption {
  value: string;
  label: string;
}

export interface SortDropdownProps {
  options: SortDropdownOption[];
  selectedOption?: SortDropdownOption | null;
  onSelect: (option: SortDropdownOption) => void;
  query?: string | "";
  orderBy?: string | "";
}

export interface SortWrapperProps {
  props: SortDropdownProps;
}

const SortControl: React.FC<SortWrapperProps> = ({ props }) => {
  const defOptVal:string = DEFAULT_OPTION;
  const [hasCollection, setHasCollection] = useState(false);
  const options = props.options;
  const defSort:SortDropdownOption = options.filter(opt => opt.value == defOptVal)[0];
  const [isOpen, setIsOpen] = useState(false);
  const [selectedOption, setSelection] = useState(defSort);
  const [currentIndex, setCurrentIndex] = useState(-1);

  const checkForCollection = (query:string): boolean => /~"[a-zA-Z0-9 #-_.://]+"/.test(query);

  const hasClassInSelfOrParent = (node: Element | null, className: string): boolean => {
    if (node !== null && node.classList.contains(className)) {
      return true;
    }

    while (node && node.parentNode && node.parentNode !== document) {
      node = node.parentNode as Element;
      if (node.classList.contains(className)) {
        return true;
      }
    }

    return false;
  };

  const autoHideListener = (event: any) => {
    if (event.type === "keydown" && event.key === "Escape") {
      setIsOpen(false);
    } else if (event.type !== "keydown") {
      if (!hasClassInSelfOrParent(event.target, "sort-control")) {
        setIsOpen(false);
      }
    }
  };

  const handleArrowKeys = (event:KeyboardEvent<HTMLDivElement>) => {
    const rowCount = options.length;
    if (event.key === 'ArrowDown') {
      setCurrentIndex((prevIndex) => (prevIndex + 1) % rowCount);
    } else if (event.key === 'ArrowUp') {
      setCurrentIndex((prevIndex) => (prevIndex - 1 + rowCount) % rowCount);
    } else if (event.key === 'Enter') {
      if (!isOpen) {
        setIsOpen(true);
      } else {
        handleOptionClick(options[currentIndex]);
      }
    }
  };

  const handleQueryChange = (e: any) => {
    const newQuery = e.detail.query ? (" " + e.detail.query) : "";
    setHasCollection(checkForCollection(newQuery));
  };

  const handleLogoClick = (e: any) => {
    setSelection(defSort);
  };

  useEffect(() => {
    if (hasCollection) {
      const collOpt = options.filter(opt => opt.value == COLLECTION_OPTION)[0];
      setSelection(collOpt);
    } else {
      if (selectedOption.value == COLLECTION_OPTION) {
        setSelection(defSort);
      }
    }
  }, [hasCollection]);

  useEffect(() => {
    if (props.options.filter(o => o.value === props.orderBy).length > 0) {
      setSelection(props.options.filter(o => o.value === props.orderBy)[0]);
    } else {
      setSelection(defSort);
    }
    setHasCollection(checkForCollection(props.query));

    window.addEventListener("logoClick", handleLogoClick);
    window.addEventListener("queryChangeEvent", handleQueryChange);
    window.addEventListener("mouseup", autoHideListener);
    window.addEventListener("scroll", autoHideListener);
    window.addEventListener("keydown", autoHideListener);

    // Clean up the event listener when the component unmounts
    return () => {
      setCurrentIndex(-1);
      window.removeEventListener("logoClick", handleLogoClick);
      window.removeEventListener("queryChangeEvent", handleQueryChange);
      window.removeEventListener("mouseup", autoHideListener);
      window.removeEventListener("scroll", autoHideListener);
      window.removeEventListener("keydown", autoHideListener);
    };
  }, []);

  const handleOptionClick = (option: SortDropdownOption) => {
    setSelection(option);
    setIsOpen(false);
    props.onSelect(option);
  };

  return (
    <div className="outer-sort-container">
      <div className="sort-selection-label">{CONTROL_TITLE}</div>
      <div className="sort-dropdown" aria-label={CONTROL_TITLE} tabIndex={0} onKeyDown={handleArrowKeys}>
        <button className="sort-dropdown-toggle" onClick={() => setIsOpen(!isOpen)}>
          <div className="sort-selection">
            <div className="sort-selection-label">{(selectedOption ? selectedOption.label : SELECT_OPTION)}</div>
            <div className="sort-selection-icon">{downArrowIcon()}</div>
          </div>
        </button>
        {isOpen && (
          <table className="sort-dropdown-menu">
            <tbody>
            {options.map((option) => (hasCollection || option.value != COLLECTION_OPTION) && (
              <tr className={(currentIndex > -1 && options[currentIndex].value) === option.value ? "sort-dropdown-item sort-dropdown-highlight" : "sort-dropdown-item"}
                  key={option.value + "row"}
                  onClick={() => handleOptionClick(option)}
                  aria-label={option.label}>
                <td className="sort-dropdown-cell-tick" key={option.value + "tick"}>
                  {(selectedOption.value == option.value) ? tickIcon() : emptyIcon()}
                </td>
                <td className="sort-dropdown-cell" key={option.value}>
                  {option.label}
                </td>
              </tr>
            ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
};

export const sortControl = angular.module('gr.sortControl', [])
  .component('sortControl', react2angular(SortControl, ["props"]));
