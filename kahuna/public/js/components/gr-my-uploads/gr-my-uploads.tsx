import * as React from "react";
import * as angular from "angular";
import { react2angular } from "react2angular";
import { useState, useEffect, KeyboardEvent } from "react";

import "./gr-my-uploads.css";

const MY_UPLOADS = "My uploads";
const MY_UPLOADS_SHORT = "My uploads";

export interface MyUploadsProps {
  myUploads: boolean,
  onChange: (selected: boolean) => void;
}

export interface MyUploadsWrapperProps {
  props: MyUploadsProps;
}

//-- logo click event --
interface LogoClickEventDetail { showPaid: boolean }
interface LogoClickEvent extends CustomEvent<LogoClickEventDetail> {optional?: string}

//-- filter change event --
interface Filter { uploadedByMe: boolean }
interface FilterChangeEventDetail { filter: Filter }
interface FilterChangeEvent extends CustomEvent<FilterChangeEventDetail> {optional?: string}

//-- payable images event --
interface PayableImagesEventDetail { showPaid: boolean }

//-- main control --
const MyUploads: React.FC<MyUploadsWrapperProps> = ({ props }) => {

  const [myUploads, setMyUploads] = useState(props.myUploads);

  const handleCheckboxClick = () => {
    setMyUploads(prevChkd => {
      props.onChange(!prevChkd);

      //-- raise payable images event --
      const event = new CustomEvent<PayableImagesEventDetail>('setPayableImages', {
        detail: {
          showPaid: !prevChkd
        }
      });
      window.dispatchEvent(event);

      return !prevChkd;
    });
  };

  const handleKeyboard = (event:KeyboardEvent<HTMLDivElement>) => {
    if (event.code === 'Space') {
      event.preventDefault();
      event.stopPropagation();
      handleCheckboxClick();
    }
  };

  const handleLogoClick = (event: LogoClickEvent) => {
    setMyUploads(false);
  };

  const handleFilterChange = (event: FilterChangeEvent) => {
    setMyUploads(event.detail.filter.uploadedByMe);
  };

  useEffect(() => {
    window.addEventListener('logoClick', handleLogoClick);
    window.addEventListener('filterChangeEvent', handleFilterChange);
    return () => {
      window.removeEventListener('logoClick', handleLogoClick);
      window.removeEventListener('filterChangeEvent', handleFilterChange);
    };
  }, []);

  return (
    <div className="my-uploads-container" tabIndex={0} aria-label={MY_UPLOADS} onKeyDown={handleKeyboard}>
      <label className="custom-checkbox">
        <input type="checkbox" checked={myUploads} onChange={handleCheckboxClick}/>
        <div className="label-wrapper" >
          <span className="custom-span"></span>
          <span className="custom-label no-select">{MY_UPLOADS}</span>
          <span className="custom-label-short no-select">{MY_UPLOADS_SHORT}</span>
        </div>
      </label>
    </div>
  );
};

export const myUploads = angular.module('gr.myUploads', [])
  .component('myUploads', react2angular(MyUploads, ["props"]));
